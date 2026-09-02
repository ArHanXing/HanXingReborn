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

package techreborn.client.gui;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import reborncore.client.gui.GuiBase;
import techreborn.blockentity.machine.multiblock.DysonSwarmReceiverBlockEntity;
import techreborn.config.TechRebornConfig;
import techreborn.packets.serverbound.DysonReceiverBindingPayload;

/**
 * GUI of the Dyson Swarm Receiver: energy bar on the left, the binding panel
 * plus the current EU/t output line on the right.
 */
public class GuiDysonSwarmReceiver extends GuiDysonSwarmBase<DysonSwarmReceiverBlockEntity> {

	/** Left edge of the info text column. */
	private static final int TEXT_X = 44;
	private static final int OUTPUT_Y = 50;

	public GuiDysonSwarmReceiver(int syncID, final PlayerEntity player, DysonSwarmReceiverBlockEntity blockEntity) {
		super(syncID, player, blockEntity, TEXT_X);
	}

	@Override
	protected void drawMachineForeground(DrawContext drawContext, GuiBase.Layer layer, int mouseX, int mouseY) {
		builder.drawMultiEnergyBar(drawContext, this, 9, 19, (int) blockEntity.getEnergy(),
				(int) blockEntity.getMaxStoredPower(), mouseX, mouseY, 0, layer);

		long generated = Math.min(blockEntity.getDisplaySailCount() * TechRebornConfig.dysonReceiverEuPerSail,
				(long) TechRebornConfig.dysonReceiverMaxOutput);
		drawText(drawContext, Text.translatable("gui.techreborn.dyson.receiver_output",
				Text.literal(String.valueOf(generated)).formatted(Formatting.YELLOW)),
				TEXT_X, OUTPUT_Y, 0x404040, layer);
	}

	@Override
	protected void sendBinding(boolean bind) {
		ClientPlayNetworking.send(new DysonReceiverBindingPayload(blockEntity.getPos(), bind));
	}
}
