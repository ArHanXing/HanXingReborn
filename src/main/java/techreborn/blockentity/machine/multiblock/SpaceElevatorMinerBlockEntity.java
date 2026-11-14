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
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import reborncore.common.screen.BuiltScreenHandler;
import reborncore.common.screen.BuiltScreenHandlerProvider;
import reborncore.common.screen.builder.ScreenHandlerBuilder;
import reborncore.common.util.RebornInventory;
import techreborn.config.TechRebornConfig;
import techreborn.init.TRBlockEntities;
import techreborn.init.TRContent;
import techreborn.space.SpaceMachineState;
import techreborn.space.SpaceTerminalLayout;

import java.util.List;

/**
 * Space Elevator Miner Unit: an independently powered task multiblock with the same
 * slot layout as the Distillation Tower (4 inputs, 6 outputs).
 * <p>
 * Like the assembler unit it must be bound to a running
 * {@link SpaceElevatorBlockEntity} host within 128 blocks planar distance;
 * binding is stored on the item by right-clicking the host and transferred
 * into the block entity NBT when the machine is placed.
 */
public class SpaceElevatorMinerBlockEntity extends JsonMultiblockMachineBlockEntity implements BuiltScreenHandlerProvider {

	private final SpaceMachineState spaceState = new SpaceMachineState(this::markDirty);
	private final techreborn.space.task.SpaceTaskContainer taskContainer = new techreborn.space.task.SpaceTaskContainer(this::markDirty);

	public techreborn.space.task.SpaceTaskContainer getTaskContainer() { return taskContainer; }

	@Override
	public void onLoad() {
		super.onLoad();
		techreborn.space.task.SpaceTaskEngine.register(this);
	}

	@Override
	public void markRemoved() {
		techreborn.space.task.SpaceTaskEngine.unregister(this);
		super.markRemoved();
	}

	@Override
	public void beforeContentsDropped() {
		techreborn.space.task.SpaceTaskEngine.removedWithDrops(this);
	}

	@Override
	public void beforeDismantle() {
		techreborn.space.task.SpaceTaskEngine.dismantle(this);
	}

	@Override
	protected boolean isRunningRecipe() {
		return "running".equals(taskContainer.status());
	}

	/** Maximum planar (XZ) distance to the host, in blocks. */
	private static final int MAX_HOST_DISTANCE = 128;
	/** {@code Long.MIN_VALUE} = not bound. */
	private long hostPos = Long.MIN_VALUE;


	public SpaceElevatorMinerBlockEntity(BlockPos pos, BlockState state) {
		super(TRBlockEntities.SPACE_ELEVATOR_MINER, pos, state, "SpaceElevatorMiner",
				TechRebornConfig.spaceElevatorMinerMaxInput,
				TechRebornConfig.spaceElevatorMinerMaxEnergy,
				TRContent.Machine.SPACE_ELEVATOR_MINER.block, 10);
		this.inventory = new RebornInventory<>(11, "SpaceElevatorMinerBlockEntity", 64, this);
		// Task execution owns the input/output inventory; legacy recipes no longer run.
		this.crafter = null;
	}

	public SpaceMachineState getSpaceState() {
		return spaceState;
	}

	/** Trusted placement/open-screen hook, never a network claim endpoint. */
	public boolean claimSpaceOwner(ServerPlayerEntity player) {
		return player != null && getWorld() instanceof ServerWorld serverWorld
				&& player.getServerWorld() == serverWorld && spaceState.claimIfUnowned(player);
	}

	public boolean canConfigure(ServerPlayerEntity player) {
		return player != null && getWorld() instanceof ServerWorld serverWorld
				&& player.getServerWorld() == serverWorld && spaceState.canConfigure(player);
	}

	@Override
	public boolean canPlayerConfigure(PlayerEntity player) {
		return getWorld() != null && (getWorld().isClient
				|| player instanceof ServerPlayerEntity serverPlayer && canConfigure(serverPlayer));
	}

	public boolean setSpaceSelection(ServerPlayerEntity player, Identifier body, int tier, List<String> resources) {
		return canConfigure(player) && spaceState.applySelection(player, body, tier, resources);
	}

	@Override
	public void onPlace(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.onPlace(world, pos, state, placer, stack);
		if (placer instanceof ServerPlayerEntity player) {
			claimSpaceOwner(player);
		}
	}

	@Override
	public void tick(World world, BlockPos pos, BlockState state, reborncore.common.blockentity.MachineBaseBlockEntity machine) {
		super.tick(world, pos, state, machine);
		if (!world.isClient && (world.getTime() + pos.asLong()) % 40 == 0) {
			techreborn.space.SpaceModuleBinding.heartbeat(this, getHostPos());
		}
	}

	@Override
	public String getMultiblockId() {
		return "space_elevator_miner";
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

	/** Exact owner/structure binding check for task launch and every paid tick. */
	public SpaceElevatorBlockEntity getAvailableTaskHost() {
		if (world == null || world.isClient || world.getRegistryKey() != World.OVERWORLD || getHostPos() == null
				|| !world.isChunkLoaded(getHostPos())) { return null; }
		if (!(world.getBlockEntity(getHostPos()) instanceof SpaceElevatorBlockEntity host)) { return null; }
		long dx = (long) pos.getX() - getHostPos().getX();
		long dz = (long) pos.getZ() - getHostPos().getZ();
		if (dx * dx + dz * dz > (long) MAX_HOST_DISTANCE * MAX_HOST_DISTANCE || !host.isMultiblockValid()
				|| !host.isActive(reborncore.common.blockentity.RedstoneConfiguration.Element.RECIPE_PROCESSING)
				|| spaceState.ownerScope() == null || !spaceState.ownerScope().equals(host.getSpaceState().ownerScope())
				|| host.getSpaceState().ownerLocked() || !host.registerModule(pos)) { return null; }
		return host;
	}

	@Override
	public BuiltScreenHandler createScreenHandler(int syncID, final PlayerEntity player) {
		if (player instanceof ServerPlayerEntity serverPlayer) {
			claimSpaceOwner(serverPlayer);
		}
		return new ScreenHandlerBuilder("spaceelevatorminer").player(player.getInventory())
				.inventory(SpaceTerminalLayout.PLAYER_X, SpaceTerminalLayout.PLAYER_Y)
				.hotbar(SpaceTerminalLayout.PLAYER_X, SpaceTerminalLayout.HOTBAR_Y).addInventory()
				.blockEntity(this)
				.slot(0, 18, 102).slot(1, 36, 102).slot(2, 18, 120).slot(3, 36, 120)
				.outputSlot(4, 18, 166).outputSlot(5, 36, 166).outputSlot(6, 54, 166)
				.outputSlot(7, 18, 184).outputSlot(8, 36, 184).outputSlot(9, 54, 184)
				.energySlot(10, 90, 102).syncEnergyValue()
				.sync(PacketCodecs.VAR_LONG, this::getSyncedHostPos, this::setSyncedHostPos)
				.addInventory().create(this, syncID);
	}

	@Override
	public NbtCompound getClientSyncNbt(RegistryWrapper.WrapperLookup registryLookup) {
		NbtCompound tag = super.getClientSyncNbt(registryLookup);
		tag.remove(SpaceMachineState.NBT_KEY);
		tag.remove(techreborn.space.task.SpaceTaskContainer.NBT_KEY);
		return tag;
	}

	@Override
	public void readNbt(NbtCompound tagCompound, RegistryWrapper.WrapperLookup registryLookup) {
		super.readNbt(tagCompound, registryLookup);
		if (world == null || !world.isClient) {
			spaceState.readNbt(tagCompound);
			taskContainer.read(tagCompound, registryLookup);
		}
		hostPos = tagCompound.contains("hostPos") ? tagCompound.getLong("hostPos") : Long.MIN_VALUE;
	}

	@Override
	public void writeNbt(NbtCompound tagCompound, RegistryWrapper.WrapperLookup registryLookup) {
		super.writeNbt(tagCompound, registryLookup);
		spaceState.writeNbt(tagCompound);
		taskContainer.write(tagCompound, registryLookup);
		tagCompound.putLong("hostPos", hostPos);
	}
}
