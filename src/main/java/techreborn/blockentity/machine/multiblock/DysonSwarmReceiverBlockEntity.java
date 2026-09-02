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
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import reborncore.common.blockentity.MachineBaseBlockEntity;
import reborncore.common.screen.BuiltScreenHandler;
import reborncore.common.screen.BuiltScreenHandlerProvider;
import reborncore.common.screen.builder.ScreenHandlerBuilder;
import techreborn.config.TechRebornConfig;
import techreborn.init.TRBlockEntities;
import techreborn.init.TRContent;

/**
 * Dyson Swarm Receiver: converts the accumulated sail count of its bound
 * player into energy, linearly (EU/t = sail count * {@link
 * TechRebornConfig#dysonReceiverEuPerSail}, capped by {@link
 * TechRebornConfig#dysonReceiverMaxOutput}). No fuel, no recipes: the energy
 * output only depends on how many sails its player fed into a Dyson Swarm
 * Host. While producing, the front texture switches to its active variant.
 */
public class DysonSwarmReceiverBlockEntity extends DysonSwarmMachineBlockEntity implements BuiltScreenHandlerProvider {

	public DysonSwarmReceiverBlockEntity(BlockPos pos, BlockState state) {
		super(TRBlockEntities.DYSON_SWARM_RECEIVER, pos, state, "DysonSwarmReceiver",
				0, TechRebornConfig.dysonReceiverMaxEnergy,
				TRContent.Machine.DYSON_SWARM_RECEIVER.block);
	}

	@Override
	public String getMultiblockId() {
		return "dyson_swarm_receiver";
	}

	@Override
	protected boolean isHostMachine() {
		return false;
	}

	@Override
	public void tick(World world, BlockPos pos, BlockState state, MachineBaseBlockEntity blockEntity) {
		super.tick(world, pos, state, blockEntity);
		if (world == null || world.isClient) {
			return;
		}
		refreshSailCount();

		boolean producing = false;
		if (isMultiblockValid() && isBound() && displaySailCount > 0) {
			long generated = Math.min(displaySailCount * TechRebornConfig.dysonReceiverEuPerSail,
					(long) TechRebornConfig.dysonReceiverMaxOutput);
			if (generated > 0) {
				addEnergy(generated);
				producing = true;
			}
		}
		setActiveState(producing);
	}

	// PowerAcceptorBlockEntity overrides: pure producer (no energy input).
	@Override
	public long getBaseMaxOutput() {
		return TechRebornConfig.dysonReceiverMaxOutput;
	}

	@Override
	public boolean canProvideEnergy(@Nullable Direction side) {
		return true;
	}

	@Override
	public boolean canAcceptEnergy(@Nullable Direction side) {
		return false;
	}

	@Override
	public BuiltScreenHandler createScreenHandler(int syncID, final PlayerEntity player) {
		return new ScreenHandlerBuilder("dysonswarmreceiver").player(player.getInventory()).inventory().hotbar().addInventory()
				.blockEntity(this)
				.syncEnergyValue()
				.sync(PacketCodecs.STRING, this::getBoundPlayerName, this::setBoundPlayerName)
				.sync(PacketCodecs.STRING, this::getBoundPlayerUuidString, this::setBoundPlayerUuidString)
				.sync(PacketCodecs.VAR_LONG, this::getDisplaySailCount, this::setDisplaySailCount)
				.addInventory().create(this, syncID);
	}
}
