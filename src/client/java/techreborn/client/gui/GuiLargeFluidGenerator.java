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

import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import reborncore.client.gui.GuiBase;
import reborncore.client.gui.GuiBuilder;
import reborncore.common.screen.BuiltScreenHandlerProvider;
import techreborn.blockentity.generator.multiblock.LargeFluidGeneratorBlockEntity;

/**
 * Generic GUI for the large multiblock fluid generators (Large Gas Turbine,
 * Large Combustion Engine, Universal Chemical Fuel Engine). Same layout as the
 * small fluid generators (cell input/output slots, tank, energy bar, progress
 * arrow) plus the multiblock hologram button and missing-structure bar.
 */
public class GuiLargeFluidGenerator<T extends LargeFluidGeneratorBlockEntity & BuiltScreenHandlerProvider> extends GuiMultiblockMachine {

	private final T blockEntity;

	public GuiLargeFluidGenerator(int syncID, final PlayerEntity player, T blockEntity) {
		super(player, blockEntity, blockEntity.createScreenHandler(syncID, player));
		this.blockEntity = blockEntity;
	}

	@Override
	protected void drawBackground(DrawContext drawContext, final float f, final int mouseX, final int mouseY) {
		super.drawBackground(drawContext, f, mouseX, mouseY);
		final GuiBase.Layer layer = Layer.BACKGROUND;

		// Cell input / empty cell output slots (same as the small generators)
		drawSlot(drawContext, 25, 35, layer);
		drawOutputSlot(drawContext, 25, 55, layer);

		drawHologramButton(drawContext, mouseX, mouseY, layer);
	}

	@Override
	protected void drawForeground(DrawContext drawContext, final int mouseX, final int mouseY) {
		super.drawForeground(drawContext, mouseX, mouseY);
		final GuiBase.Layer layer = Layer.FOREGROUND;

		// Horizontal order: cell slots (25..43) | three gauges | progress arrow |
		// energy bar. Gauges are 22 wide and packed flush at pitch 24, so the three
		// of them span 48..118; the arrow then sits at 120..136 and the energy bar
		// at 140..154, which stays inside the 176-wide GUI and off the arrow.
		int tankCount = Math.max(1, blockEntity.getTankCount());
		final int tankX = 48;
		final int tankPitch = 24;
		for (int i = 0; i < tankCount; i++) {
			reborncore.common.util.Tank tank = blockEntity.getTank(i);
			if (tank == null) {
				continue;
			}
			builder.drawTank(drawContext, this, tankX + i * tankPitch, 25, mouseX, mouseY,
				tank.getFluidInstance(), tank.getFluidValueCapacity(), tank.isEmpty(), layer);
		}

		int progressX = tankX + tankCount * tankPitch;
		builder.drawProgressBar(drawContext, this, blockEntity.getProgressScaled(10), 100, progressX, 48, mouseX, mouseY, GuiBuilder.ProgressDirection.RIGHT, layer);
		// 14 wide, placed clear of the arrow and still inside the GUI frame.
		builder.drawMultiEnergyBar(drawContext, this, progressX + 20, 28, (int) blockEntity.getEnergy(), (int) blockEntity.getMaxStoredPower(), mouseX, mouseY, 0, layer);

		drawMissingStructureOverlay(drawContext, mouseX, mouseY, layer);
	}

}
