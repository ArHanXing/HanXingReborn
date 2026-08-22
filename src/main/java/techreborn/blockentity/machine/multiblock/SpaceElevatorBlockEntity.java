/*
 * This file is part of TechReborn, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2026 TechReborn
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package techreborn.blockentity.machine.multiblock;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtLong;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import reborncore.common.blockentity.MachineBaseBlockEntity;
import reborncore.common.blocks.BlockMachineBase;
import reborncore.common.screen.BuiltScreenHandler;
import reborncore.common.screen.BuiltScreenHandlerProvider;
import reborncore.common.screen.builder.ScreenHandlerBuilder;
import techreborn.blocks.misc.BlockSpaceElevatorPowerModule;
import techreborn.config.TechRebornConfig;
import techreborn.init.TRBlockEntities;
import techreborn.init.TRContent;
import techreborn.multiblock.CoilHeatScanner;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Space Elevator host: the power hub of the elevator multiblock family.
 * <p>
 * It has no recipe system. While it is powered (2048 EU/t, EV tier), the
 * structure is valid and the machine is in the Overworld it "runs": every
 * {@link BlockSpaceElevatorPowerModule} inside the JSON structure switches to
 * its active (lit) state.
 * <p>
 * Bound assembler/miner units register themselves here; the host keeps the
 * module list alive (units are dropped once they leave range or are broken)
 * and shows a read-only module summary in its GUI.
 */
public class SpaceElevatorBlockEntity extends JsonMultiblockMachineBlockEntity implements BuiltScreenHandlerProvider {

	/** How often the bound module list is refreshed (ticks). */
	private static final int MODULE_CHECK_INTERVAL = 40;
	/** Maximum number of units that can be bound to one host. */
	public static final int MAX_MODULES = 8;

	private final List<BlockPos> modules = new ArrayList<>();
	private int checkCounter = 0;
	private boolean running = false;
	private boolean powerModulesActive = false;
	/**
	 * Bound module summary, synced to the GUI: one line per unit in the form
	 * "A x y z" (assembler) or "M x y z" (miner), newline separated.
	 */
	private String moduleSummary = "";

	public SpaceElevatorBlockEntity(BlockPos pos, BlockState state) {
		super(TRBlockEntities.SPACE_ELEVATOR, pos, state, "SpaceElevator",
				TechRebornConfig.spaceElevatorMaxInput, TechRebornConfig.spaceElevatorMaxEnergy,
				TRContent.Machine.SPACE_ELEVATOR.block, -1);
	}

	@Override
	public String getMultiblockId() {
		return "space_elevator";
	}

	/**
	 * @return {@code true} while the elevator is powered, in the Overworld and
	 *         its structure is valid.
	 */
	public boolean isRunning() {
		return running;
	}

	/**
	 * Registers a bound unit (assembler or miner) at this host. Idempotent;
	 * stale entries are removed by the periodic refresh.
	 *
	 * @param modulePos {@link BlockPos} the unit controller position
	 * @return {@code false} if the host already has the maximum number of
	 *         bound units and this one is not registered yet
	 */
	public boolean registerModule(BlockPos modulePos) {
		if (modules.contains(modulePos)) {
			return true;
		}
		if (modules.size() >= MAX_MODULES) {
			return false;
		}
		modules.add(modulePos);
		return true;
	}

	/**
	 * @return {@link String} one line per bound module: "A x y z" for an
	 *         assembler unit or "M x y z" for a miner unit, newline separated.
	 */
	public String getModuleSummary() {
		return moduleSummary;
	}

	public void setModuleSummary(String moduleSummary) {
		this.moduleSummary = moduleSummary;
	}

	@Override
	public void tick(World world, BlockPos pos, BlockState state, MachineBaseBlockEntity blockEntity) {
		super.tick(world, pos, state, blockEntity);
		if (world == null || world.isClient) {
			return;
		}

		// Overworld only; burns 2048 EU/t (1A EV) while the structure is valid.
		boolean canRun = world.getRegistryKey() == World.OVERWORLD && isMultiblockValid();
		boolean hasPower = false;
		if (canRun) {
			long cost = TechRebornConfig.spaceElevatorEnergyPerTick;
			if (getEnergy() >= cost) {
				useEnergy(cost);
				hasPower = true;
			}
		}
		running = canRun && hasPower;

		// Self blockstate (front texture off/on) and power module bloom only
		// change when the run state flips.
		if (running != powerModulesActive) {
			powerModulesActive = running;
			((BlockMachineBase) state.getBlock()).setActive(running, world, pos);
			updatePowerModuleState(running);
		}

		if (++checkCounter >= MODULE_CHECK_INTERVAL) {
			checkCounter = 0;
			refreshModules();
		}
	}

	/**
	 * Switches every power module inside the JSON structure to the given
	 * active state (self-adapting: positions are derived from the structure
	 * definition, no hard-coded coordinates).
	 */
	private void updatePowerModuleState(boolean active) {
		for (BlockPos partPos : CoilHeatScanner.collectPositions(pos, getFacing(), getMultiblockId())) {
			BlockState partState = world.getBlockState(partPos);
			if (partState.getBlock() instanceof BlockSpaceElevatorPowerModule) {
				world.setBlockState(partPos, partState.with(BlockSpaceElevatorPowerModule.ACTIVE, active), 2);
			}
		}
	}

	/**
	 * Drops bound units that are no longer a valid assembler/miner bound to
	 * this host (broken, moved out of range or unbound) and rebuilds the
	 * module summary.
	 */
	private void refreshModules() {
		StringBuilder sb = new StringBuilder();
		Iterator<BlockPos> it = modules.iterator();
		while (it.hasNext()) {
			BlockPos modulePos = it.next();
			BlockEntity be = world.getBlockEntity(modulePos);
			if (be instanceof SpaceElevatorAssemblerBlockEntity assembler && assembler.isBoundTo(this)) {
				sb.append('A').append(' ').append(modulePos.getX()).append(' ').append(modulePos.getY()).append(' ').append(modulePos.getZ()).append('\n');
			} else if (be instanceof SpaceElevatorMinerBlockEntity miner && miner.isBoundTo(this)) {
				sb.append('M').append(' ').append(modulePos.getX()).append(' ').append(modulePos.getY()).append(' ').append(modulePos.getZ()).append('\n');
			} else {
				it.remove();
			}
		}
		moduleSummary = sb.toString();
		syncWithAll();
	}

	// IContainerProvider
	@Override
	public BuiltScreenHandler createScreenHandler(int syncID, final PlayerEntity player) {
		return new ScreenHandlerBuilder("spaceelevator").player(player.getInventory()).inventory().hotbar().addInventory()
				.blockEntity(this).syncEnergyValue()
				.sync(PacketCodecs.STRING, this::getModuleSummary, this::setModuleSummary)
				.addInventory().create(this, syncID);
	}

	@Override
	public void readNbt(NbtCompound tagCompound, RegistryWrapper.WrapperLookup registryLookup) {
		super.readNbt(tagCompound, registryLookup);
		modules.clear();
		NbtList list = tagCompound.getList("modules", NbtElement.LONG_TYPE);
		for (NbtElement element : list) {
			modules.add(BlockPos.fromLong(((NbtLong) element).longValue()));
		}
	}

	@Override
	public void writeNbt(NbtCompound tagCompound, RegistryWrapper.WrapperLookup registryLookup) {
		super.writeNbt(tagCompound, registryLookup);
		NbtList list = new NbtList();
		for (BlockPos modulePos : modules) {
			list.add(NbtLong.of(modulePos.asLong()));
		}
		tagCompound.put("modules", list);
	}
}
