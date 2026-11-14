/*
 * This file is part of RebornCore, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021 TeamReborn
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

package reborncore.common.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import reborncore.common.blockentity.FluidConfiguration;
import reborncore.common.blockentity.MachineBaseBlockEntity;
import reborncore.common.blockentity.SlotConfiguration;
import reborncore.common.chunkloading.ChunkLoaderManager;
import reborncore.common.network.clientbound.FluidConfigSyncPayload;
import reborncore.common.network.clientbound.SlotSyncPayload;
import reborncore.common.network.serverbound.*;
import reborncore.common.screen.BuiltScreenHandler;

public class ServerBoundPackets {

	/** Resolve mutations from the actual open machine, never just a client position. */
	@Nullable
	private static MachineBaseBlockEntity configurableMachine(BlockPosPayload payload, ServerPlayerEntity player) {
		if (!(player.currentScreenHandler instanceof BuiltScreenHandler screen)
				|| !screen.getPos().equals(payload.pos()) || !screen.canUse(player)) {
			return null;
		}
		MachineBaseBlockEntity machine = screen.getBlockEntity();
		if (machine.getWorld() != player.getWorld()
				|| player.getWorld().getBlockEntity(payload.pos()) != machine
				|| !machine.canPlayerConfigure(player)) {
			return null;
		}
		return machine;
	}

	public static void init() {
		ServerPlayNetworking.registerGlobalReceiver(FluidConfigSavePayload.ID, (payload, context) -> {
			var machine = configurableMachine(payload, context.player());
			if (machine == null || machine.fluidConfiguration == null) {
				return;
			}
			FluidConfiguration.TankConfigHolder holder = machine.fluidConfiguration.getTankDetails(payload.tankID());
			if (holder == null) {
				return;
			}
			holder.updateFluidConfig(payload.fluidConfiguration());
			machine.markDirty();

			NetworkManager.sendToTracking(new FluidConfigSyncPayload(payload.pos(), machine.fluidConfiguration), machine);

			// Let pipes detect changed connections.
			World world = machine.getWorld();
			BlockState blockState = world.getBlockState(machine.getPos());
			world.updateNeighborsAlways(machine.getPos(), blockState.getBlock());
		});

		ServerPlayNetworking.registerGlobalReceiver(SlotConfigSavePayload.ID, (payload, context) -> {
			var machine = configurableMachine(payload, context.player());
			if (machine == null || machine.getSlotConfiguration() == null) {
				return;
			}
			// Validate the complete batch before applying any part of it.
			for (SlotConfiguration.SlotConfigHolder detail : payload.slotConfig().getSlotDetails()) {
				if (machine.getSlotConfiguration().getSlotDetails(detail.getSlotID()) == null) {
					return;
				}
			}
			for (SlotConfiguration.SlotConfigHolder detail : payload.slotConfig().getSlotDetails()) {
				machine.getSlotConfiguration().updateSlotDetails(detail);
			}
			machine.markDirty();
			NetworkManager.sendToWorld(new SlotSyncPayload(payload.pos(), machine.getSlotConfiguration()), (ServerWorld) machine.getWorld());
		});

		ServerPlayNetworking.registerGlobalReceiver(FluidIoSavePayload.ID, (payload, context) -> {
			var machine = configurableMachine(payload, context.player());
			if (machine == null || machine.fluidConfiguration == null) {
				return;
			}
			FluidConfiguration.TankConfigHolder holder = machine.fluidConfiguration.getTankDetails(payload.tankID());
			if (holder == null) {
				return;
			}
			holder.setInput(payload.input());
			holder.setOutput(payload.output());
			machine.markDirty();
			NetworkManager.sendToTracking(new FluidConfigSyncPayload(payload.pos(), machine.fluidConfiguration), machine);
		});

		ServerPlayNetworking.registerGlobalReceiver(IoSavePayload.ID, (payload, context) -> {
			var machine = configurableMachine(payload, context.player());
			if (machine == null || machine.getSlotConfiguration() == null) {
				return;
			}
			SlotConfiguration.SlotConfigHolder holder = machine.getSlotConfiguration().getSlotDetails(payload.slotID());
			if (holder == null) {
				return;
			}
			holder.setInput(payload.input());
			holder.setOutput(payload.output());
			holder.setFilter(payload.filter());
			holder.setPriority(payload.priority());
			machine.markDirty();
			NetworkManager.sendToAll(new SlotSyncPayload(payload.pos(), machine.getSlotConfiguration()), context.player().getServer());
		});

		ServerPlayNetworking.registerGlobalReceiver(SlotSavePayload.ID, (payload, context) -> {
			var machine = configurableMachine(payload, context.player());
			if (machine == null || machine.getSlotConfiguration() == null) {
				return;
			}
			SlotConfiguration.SlotConfigHolder holder = machine.getSlotConfiguration().getSlotDetails(payload.slotConfig().getSlotID());
			if (holder == null) {
				return;
			}
			holder.updateSlotConfig(payload.slotConfig());
			machine.markDirty();
			NetworkManager.sendToWorld(new SlotSyncPayload(payload.pos(), machine.getSlotConfiguration()), (ServerWorld) machine.getWorld());
		});

		ServerPlayNetworking.registerGlobalReceiver(ChunkLoaderRequestPayload.ID, (payload, context) -> {
			payload.getBlockEntity(MachineBaseBlockEntity.class, context.player());
			ChunkLoaderManager chunkLoaderManager = ChunkLoaderManager.get(context.player().getWorld());
			chunkLoaderManager.syncChunkLoaderToClient(context.player(), payload.pos());
		});

		ServerPlayNetworking.registerGlobalReceiver(SetRedstoneStatePayload.ID, (payload, context) -> {
			var machine = configurableMachine(payload, context.player());
			if (machine == null || !machine.getRedstoneElements().contains(payload.element())) {
				return;
			}
			machine.setRedstoneConfiguration(machine.getRedstoneConfiguration().withState(payload.element(), payload.state()));
			machine.markDirty();
		});
	}
}
