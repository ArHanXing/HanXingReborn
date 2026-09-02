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

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import reborncore.common.blocks.BlockMachineBase;
import techreborn.world.DysonSwarmData;

import java.util.UUID;

/**
 * Common base of the two Dyson Swarm multiblock machines (host and
 * receiver).
 * <p>
 * Both machines work per player: a machine is bound to exactly one player
 * (UUID) through its GUI, and a player can have at most one host and one
 * receiver bound at the same time (enforced through {@link DysonSwarmData},
 * stored in the overworld's level data). While bound the machines show the
 * bound player's name/UUID and sail count in their GUIs; unbound machines do
 * not work.
 */
public abstract class DysonSwarmMachineBlockEntity extends JsonMultiblockMachineBlockEntity {

	@Nullable
	protected UUID boundPlayerUuid = null;
	protected String boundPlayerName = "";
	protected String boundPlayerUuidString = "";

	/** Sail count of the bound player, refreshed from {@link DysonSwarmData} and synced to the GUI. */
	protected long displaySailCount = 0;

	public DysonSwarmMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, String name,
			int maxInput, int maxEnergy, Block toolDrop) {
		super(type, pos, state, name, maxInput, maxEnergy, toolDrop, -1);
	}

	/**
	 * @return {@code true} for the Dyson Swarm Host (consumes sails), {@code false}
	 *         for the Dyson Swarm Receiver (produces energy)
	 */
	protected abstract boolean isHostMachine();

	/**
	 * @return the binding state as it is valid in {@link DysonSwarmData} (the
	 *         data is authoritative, the NBT fields are only kept for display)
	 */
	public boolean isBound() {
		World world = getWorld();
		if (world == null || boundPlayerUuid == null) {
			return false;
		}
		if (world.isClient) {
			// Client side: the server syncs the fields through the GUI, the data
			// itself never leaves the server.
			return true;
		}
		DysonSwarmData data = DysonSwarmData.get(world);
		if (data == null) {
			return false;
		}
		String dimension = dimensionKey(world);
		return isHostMachine()
				? data.isHostBoundAt(boundPlayerUuid, dimension, getPos())
				: data.isReceiverBoundAt(boundPlayerUuid, dimension, getPos());
	}

	/**
	 * Entry point called from the bind/unbind GUI buttons (via the server
	 * bound packet handler). Gives the interacting player textual feedback.
	 *
	 * @param bind   {@code boolean} {@code true} = bind, {@code false} = unbind
	 * @param player {@link ServerPlayerEntity} the player clicking the button
	 */
	public void handleBindingAction(boolean bind, ServerPlayerEntity player) {
		if (player == null) {
			return;
		}
		if (bind) {
			if (boundPlayerUuid != null && !boundPlayerUuid.equals(player.getUuid())) {
				player.sendMessage(Text.translatable("message.techreborn.dyson.bound_to_other"), false);
				return;
			}
			if (!bindToPlayer(player)) {
				player.sendMessage(Text.translatable("message.techreborn.dyson.already_bound_elsewhere"), false);
			}
		} else if (!unbindFromPlayer(player)) {
			player.sendMessage(Text.translatable("message.techreborn.dyson.not_bound"), false);
		}
	}

	/**
	 * Tries to bind this machine to the given (interacting) player. Fails when
	 * the player already has another live machine of the same kind bound.
	 *
	 * @param player {@link ServerPlayerEntity} the player clicking the bind button
	 * @return {@code true} when the binding succeeded
	 */
	public boolean bindToPlayer(ServerPlayerEntity player) {
		World world = getWorld();
		if (world == null || world.isClient || player == null) {
			return false;
		}
		DysonSwarmData data = DysonSwarmData.get(world);
		if (data == null) {
			return false;
		}
		UUID uuid = player.getUuid();
		String dimension = dimensionKey(world);

		// The machine is already bound to this very player at this very machine.
		if (boundPlayerUuid != null && boundPlayerUuid.equals(uuid) && isBound()) {
			return true;
		}
		// Free the previous binding of this machine (if any) before re-binding it.
		if (boundPlayerUuid != null) {
			clearBinding(data, boundPlayerUuid, dimension);
		}

		DysonSwarmData.Binding existing = isHostMachine()
				? data.getHostBinding(uuid)
				: data.getReceiverBinding(uuid);
		if (existing != null && !existing.matches(dimension, getPos()) && isMachineAlive(uuid, existing)) {
			// The player already has another live machine of this kind.
			return false;
		}

		if (isHostMachine()) {
			data.bindHost(uuid, dimension, getPos());
		} else {
			data.bindReceiver(uuid, dimension, getPos());
		}
		boundPlayerUuid = uuid;
		boundPlayerName = player.getName().getString();
		boundPlayerUuidString = uuid.toString();
		markDirty();
		syncWithAll();
		return true;
	}

	/**
	 * Unbinds this machine from its bound player. Only the bound player
	 * himself may unbind (through the GUI).
	 *
	 * @param player {@link ServerPlayerEntity} the player clicking the unbind button
	 * @return {@code true} when the machine was unbound
	 */
	public boolean unbindFromPlayer(ServerPlayerEntity player) {
		World world = getWorld();
		if (world == null || world.isClient || player == null || boundPlayerUuid == null) {
			return false;
		}
		if (!boundPlayerUuid.equals(player.getUuid())) {
			return false;
		}
		DysonSwarmData data = DysonSwarmData.get(world);
		if (data != null) {
			clearBinding(data, boundPlayerUuid, dimensionKey(world));
		}
		boundPlayerUuid = null;
		boundPlayerName = "";
		boundPlayerUuidString = "";
		markDirty();
		syncWithAll();
		return true;
	}

	private void clearBinding(DysonSwarmData data, UUID uuid, String dimension) {
		if (isHostMachine()) {
			data.unbindHost(uuid, dimension, getPos());
		} else {
			data.unbindReceiver(uuid, dimension, getPos());
		}
	}

	/**
	 * Checks whether the machine the given binding points at still exists and
	 * is bound to the same player (used to allow re-binding when the old
	 * machine was destroyed).
	 */
	private boolean isMachineAlive(UUID uuid, DysonSwarmData.Binding binding) {
		World world = getWorld();
		if (world == null || world.getServer() == null) {
			return false;
		}
		ServerWorld otherWorld = world.getServer()
				.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier.of(binding.dimension())));
		if (otherWorld == null) {
			return false;
		}
		BlockPos otherPos = BlockPos.fromLong(binding.pos());
		if (otherWorld.getBlockEntity(otherPos) instanceof DysonSwarmMachineBlockEntity other) {
			return other.isHostMachine() == isHostMachine() && uuid.equals(other.boundPlayerUuid);
		}
		return false;
	}

	private static String dimensionKey(World world) {
		return world.getRegistryKey().getValue().toString();
	}

	/**
	 * Refreshes the sail count shown in the GUI from {@link DysonSwarmData}.
	 * Called every tick on the server.
	 */
	protected void refreshSailCount() {
		World world = getWorld();
		DysonSwarmData data = world == null ? null : DysonSwarmData.get(world);
		displaySailCount = data == null ? 0 : data.getSailCount(boundPlayerUuid);
	}

	public String getBoundPlayerName() {
		return boundPlayerName;
	}

	public void setBoundPlayerName(String boundPlayerName) {
		this.boundPlayerName = boundPlayerName;
	}

	public String getBoundPlayerUuidString() {
		return boundPlayerUuidString;
	}

	public void setBoundPlayerUuidString(String boundPlayerUuidString) {
		this.boundPlayerUuidString = boundPlayerUuidString;
	}

	public long getDisplaySailCount() {
		return displaySailCount;
	}

	public void setDisplaySailCount(long displaySailCount) {
		this.displaySailCount = displaySailCount;
	}

	/**
	 * Flips the machine's {@code active} blockstate to the given value when it
	 * changed (front texture switches between the off/on variants).
	 */
	protected void setActiveState(boolean active) {
		World world = getWorld();
		if (world == null || world.isClient) {
			return;
		}
		BlockState state = world.getBlockState(getPos());
		if (state.getBlock() instanceof BlockMachineBase && state.get(BlockMachineBase.ACTIVE) != active) {
			world.setBlockState(getPos(), state.with(BlockMachineBase.ACTIVE, active));
		}
	}

	@Override
	public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		super.readNbt(nbt, registryLookup);
		if (nbt.containsUuid("boundUuid")) {
			boundPlayerUuid = nbt.getUuid("boundUuid");
		} else {
			boundPlayerUuid = null;
		}
		boundPlayerName = nbt.getString("boundName");
		boundPlayerUuidString = boundPlayerUuid == null ? "" : boundPlayerUuid.toString();
	}

	@Override
	public void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		super.writeNbt(nbt, registryLookup);
		if (boundPlayerUuid != null) {
			nbt.putUuid("boundUuid", boundPlayerUuid);
			nbt.putString("boundName", boundPlayerName);
		}
	}

	@Override
	public boolean canBeUpgraded() {
		return false;
	}
}
