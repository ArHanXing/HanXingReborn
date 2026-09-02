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
import net.minecraft.item.ItemStack;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import reborncore.common.blockentity.MachineBaseBlockEntity;
import reborncore.common.screen.BuiltScreenHandler;
import reborncore.common.screen.BuiltScreenHandlerProvider;
import reborncore.common.screen.builder.ScreenHandlerBuilder;
import reborncore.common.util.RebornInventory;
import techreborn.config.TechRebornConfig;
import techreborn.init.TRBlockEntities;
import techreborn.init.TRContent;
import techreborn.world.DysonSwarmData;

/**
 * Dyson Swarm Host: consumes solar sails and permanently increases the sail
 * count of the bound player (stored in {@link DysonSwarmData}). It has no
 * recipe system and needs no energy - launching is free once the sails are
 * fed in. While bound, valid and fed with sails the front texture switches to
 * its active variant.
 * <p>
 * The receiver of the same player turns the accumulated sail count into
 * energy, so the host can be fed and forgotten.
 */
public class DysonSwarmHostBlockEntity extends DysonSwarmMachineBlockEntity implements BuiltScreenHandlerProvider {

	private static final int SAIL_SLOT = 0;

	private int launchCounter = 0;

	public DysonSwarmHostBlockEntity(BlockPos pos, BlockState state) {
		super(TRBlockEntities.DYSON_SWARM_HOST, pos, state, "DysonSwarmHost",
				0, 0,
				TRContent.Machine.DYSON_SWARM_HOST.block);
		this.inventory = new RebornInventory<>(1, "DysonSwarmHostBlockEntity", 64, this);
	}

	@Override
	public String getMultiblockId() {
		return "dyson_swarm_host";
	}

	@Override
	protected boolean isHostMachine() {
		return true;
	}

	@Override
	public void tick(World world, BlockPos pos, BlockState state, MachineBaseBlockEntity blockEntity) {
		super.tick(world, pos, state, blockEntity);
		if (world == null || world.isClient) {
			return;
		}
		refreshSailCount();

		boolean valid = isMultiblockValid();
		boolean bound = isBound();
		boolean hasSail = hasSailInSlot();

		if (valid && bound && hasSail) {
			// Launch one solar sail per interval; every launched sail adds one
			// to the bound player's permanent sail count.
			if (++launchCounter >= TechRebornConfig.dysonHostLaunchIntervalTicks) {
				launchCounter = 0;
				ItemStack stack = inventory.getStack(SAIL_SLOT);
				if (!stack.isEmpty() && stack.isOf(TRContent.SOLAR_SAIL)) {
					stack.decrement(1);
					DysonSwarmData data = DysonSwarmData.get(world);
					if (data != null && boundPlayerUuid != null) {
						data.addSails(boundPlayerUuid, 1);
					}
					markDirty();
				}
			}
		} else {
			launchCounter = 0;
		}

		setActiveState(valid && bound && hasSail);
	}

	private boolean hasSailInSlot() {
		ItemStack stack = inventory.getStack(SAIL_SLOT);
		return !stack.isEmpty() && stack.isOf(TRContent.SOLAR_SAIL);
	}

	@Override
	public BuiltScreenHandler createScreenHandler(int syncID, final PlayerEntity player) {
		return new ScreenHandlerBuilder("dysonswarmhost").player(player.getInventory()).inventory().hotbar().addInventory()
				.blockEntity(this)
				.slot(SAIL_SLOT, 44, 34, stack -> stack.isOf(TRContent.SOLAR_SAIL))
				.sync(PacketCodecs.STRING, this::getBoundPlayerName, this::setBoundPlayerName)
				.sync(PacketCodecs.STRING, this::getBoundPlayerUuidString, this::setBoundPlayerUuidString)
				.sync(PacketCodecs.VAR_LONG, this::getDisplaySailCount, this::setDisplaySailCount)
				.addInventory().create(this, syncID);
	}
}
