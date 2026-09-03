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
import techreborn.blockentity.machine.multiblock.DysonSwarmHostBlockEntity;
import techreborn.packets.serverbound.DysonHostBindingPayload;

/**
 * GUI of the Dyson Swarm Host: a single solar sail input slot on the left,
 * the binding panel (bound player, sail count, bind/unbind buttons) on the
 * right.
 */
public class GuiDysonSwarmHost extends GuiDysonSwarmBase<DysonSwarmHostBlockEntity> {

	/** Left edge of the info text column. */
	private static final int TEXT_X = 35;
	/** Solar sail input slot (must match the ScreenHandler slot position). */
	private static final int SLOT_X = 44;
	private static final int SLOT_Y = 34;

	public GuiDysonSwarmHost(int syncID, final PlayerEntity player, DysonSwarmHostBlockEntity blockEntity) {
		super(syncID, player, blockEntity, TEXT_X);
	}

	@Override
	protected void drawMachineBackground(DrawContext drawContext, int mouseX, int mouseY) {
		drawSlot(drawContext, SLOT_X, SLOT_Y, Layer.BACKGROUND);
	}

	@Override
	protected void sendBinding(boolean bind) {
		ClientPlayNetworking.send(new DysonHostBindingPayload(blockEntity.getPos(), bind));
	}
}
