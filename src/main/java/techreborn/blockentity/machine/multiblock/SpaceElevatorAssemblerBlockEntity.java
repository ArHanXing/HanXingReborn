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
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import reborncore.common.crafting.RebornRecipe;
import reborncore.common.recipes.RecipeCrafter;
import reborncore.common.screen.BuiltScreenHandler;
import reborncore.common.screen.BuiltScreenHandlerProvider;
import reborncore.common.screen.builder.ScreenHandlerBuilder;
import reborncore.common.util.RebornInventory;
import techreborn.config.TechRebornConfig;
import techreborn.init.ModRecipes;
import techreborn.init.TRBlockEntities;
import techreborn.init.TRContent;

/**
 * Space Elevator Assembler Unit: an independent-recipe multiblock with the
 * same slot layout as the Precise Assembler (16 inputs, 4 outputs).
 * <p>
 * It must be bound to a {@link SpaceElevatorBlockEntity} host within a planar
 * distance of 128 blocks; the recipe only runs while the host is running
 * (powered, Overworld, valid structure). Binding is done by right-clicking
 * the host with this machine's item, which stores the host position on the
 * item; placing the machine transfers it into the block entity NBT.
 */
public class SpaceElevatorAssemblerBlockEntity extends JsonMultiblockMachineBlockEntity implements BuiltScreenHandlerProvider {

	/** How often the host binding is re-checked (ticks). */
	private static final int HOST_CHECK_INTERVAL = 40;
	/** Maximum planar (XZ) distance to the host, in blocks. */
	private static final int MAX_HOST_DISTANCE = 128;
	/** {@code Long.MIN_VALUE} = not bound. */
	private long hostPos = Long.MIN_VALUE;

	private boolean hostCheckCache = false;
	private long hostCheckTick = -1;

	public SpaceElevatorAssemblerBlockEntity(BlockPos pos, BlockState state) {
		super(TRBlockEntities.SPACE_ELEVATOR_ASSEMBLER, pos, state, "SpaceElevatorAssembler",
				TechRebornConfig.spaceElevatorAssemblerMaxInput,
				TechRebornConfig.spaceElevatorAssemblerMaxEnergy,
				TRContent.Machine.SPACE_ELEVATOR_ASSEMBLER.block, 20);
		final int[] inputs = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
		final int[] outputs = new int[]{16, 17, 18, 19};
		// 16 inputs + 4 outputs + 1 energy slot (slot 20)
		this.inventory = new RebornInventory<>(21, "SpaceElevatorAssemblerBlockEntity", 64, this);
		this.crafter = new RecipeCrafter(ModRecipes.SPACE_ELEVATOR_ASSEMBLER, this, 16, 4, this.inventory, inputs, outputs);
		this.crafter.setMaxParallel(16);
	}

	@Override
	public String getMultiblockId() {
		return "space_elevator_assembler";
	}

	/**
	 * @param host {@link SpaceElevatorBlockEntity} the host to test against
	 * @return {@code true} if this unit is bound to the given host
	 */
	public boolean isBoundTo(SpaceElevatorBlockEntity host) {
		return hostPos == host.getPos().asLong();
	}

	/**
	 * @return {@link BlockPos} the bound host position, or {@code null} if
	 *         not bound
	 */
	@Nullable
	public BlockPos getHostPos() {
		return hostPos == Long.MIN_VALUE ? null : BlockPos.fromLong(hostPos);
	}

	/**
	 * Raw host position for GUI sync ({@code Long.MIN_VALUE} = not bound).
	 */
	public long getSyncedHostPos() {
		return hostPos;
	}

	public void setSyncedHostPos(long hostPos) {
		this.hostPos = hostPos;
	}

	@Override
	public boolean canCraft(RebornRecipe rebornRecipe) {
		return isMultiblockValid() && isHostAvailable();
	}

	/**
	 * Cached host check: the host must exist, be running and be within the
	 * planar distance limit. Re-validated every {@value #HOST_CHECK_INTERVAL}
	 * ticks; while valid, this unit also re-registers itself at the host.
	 */
	private boolean isHostAvailable() {
		World world = getWorld();
		if (world == null) {
			return false;
		}
		long now = world.getTime();
		if (hostCheckTick != -1 && now - hostCheckTick < HOST_CHECK_INTERVAL) {
			return hostCheckCache;
		}
		hostCheckTick = now;
		hostCheckCache = doCheckHost(world);
		return hostCheckCache;
	}

	private boolean doCheckHost(World world) {
		if (hostPos == Long.MIN_VALUE) {
			return false;
		}
		// The elevator only exists in the Overworld.
		if (world.getRegistryKey() != World.OVERWORLD) {
			return false;
		}
		BlockPos hostBlockPos = getHostPos();
		if (!(world.getBlockEntity(hostBlockPos) instanceof SpaceElevatorBlockEntity host) || !host.isRunning()) {
			return false;
		}
		long dx = pos.getX() - hostBlockPos.getX();
		long dz = pos.getZ() - hostBlockPos.getZ();
		if (dx * dx + dz * dz > (long) MAX_HOST_DISTANCE * MAX_HOST_DISTANCE) {
			return false;
		}
		// Keep ourselves alive in the host's module list; the host rejects new
		// units once it has the maximum number bound.
		if (!host.registerModule(pos)) {
			return false;
		}
		return true;
	}

	@Override
	public BuiltScreenHandler createScreenHandler(int syncID, final PlayerEntity player) {
		var builder = new ScreenHandlerBuilder("spaceelevatorassembler").player(player.getInventory())
				.inventory().hotbar().addInventory().blockEntity(this);
		// 16 inputs: 4 columns x 4 rows
		int[] xs = new int[]{35, 53, 71, 89};
		int[] ys = new int[]{17, 35, 53, 71};
		int slot = 0;
		for (int y : ys) {
			for (int x : xs) {
				builder.slot(slot++, x, y);
			}
		}
		// 4 outputs: 2 columns x 2 rows
		builder.outputSlot(16, 125, 17).outputSlot(17, 143, 17).outputSlot(18, 125, 35).outputSlot(19, 143, 35)
				.energySlot(20, 8, 72)
				.syncEnergyValue().syncCrafterValue()
				.sync(PacketCodecs.VAR_LONG, this::getSyncedHostPos, this::setSyncedHostPos);
		return builder.addInventory().create(this, syncID);
	}

	@Override
	public void readNbt(NbtCompound tagCompound, RegistryWrapper.WrapperLookup registryLookup) {
		super.readNbt(tagCompound, registryLookup);
		hostPos = tagCompound.getLong("hostPos");
	}

	@Override
	public void writeNbt(NbtCompound tagCompound, RegistryWrapper.WrapperLookup registryLookup) {
		super.writeNbt(tagCompound, registryLookup);
		tagCompound.putLong("hostPos", hostPos);
	}
}
