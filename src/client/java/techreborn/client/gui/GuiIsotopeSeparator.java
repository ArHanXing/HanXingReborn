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
import net.minecraft.text.Text;

import reborncore.client.gui.GuiBase;
import reborncore.client.gui.GuiBuilder;
import reborncore.common.screen.BuiltScreenHandler;

import techreborn.blockentity.machine.multiblock.IsotopeSeparatorBlockEntity;

/**
 * GUI of the Isotope Separator.
 * <p>
 * On top of the usual slots and bars it shows the three numbers that drive the
 * machine: the incoming redstone signal, the resulting rotation speed and the
 * rotor's speed cap, plus a red warning once the speed passes the point where
 * the separation starts losing a grade.
 */
public class GuiIsotopeSeparator extends GuiBase<BuiltScreenHandler> {

	final IsotopeSeparatorBlockEntity blockEntity;

	public GuiIsotopeSeparator(int syncID, final PlayerEntity player, final IsotopeSeparatorBlockEntity blockEntity) {
		super(player, blockEntity, blockEntity.createScreenHandler(syncID, player));
		this.blockEntity = blockEntity;
	}

	@Override
	protected void drawBackground(DrawContext drawContext, final float f, final int mouseX, final int mouseY) {
		super.drawBackground(drawContext, f, mouseX, mouseY);
		final Layer layer = Layer.BACKGROUND;

		// energy
		drawSlot(drawContext, 8, 72, layer);
		// feed and rotor
		drawSlot(drawContext, 35, 35, layer);
		drawSlot(drawContext, 55, 35, layer);
		// product and tails
		drawSlot(drawContext, 107, 26, layer);
		drawSlot(drawContext, 125, 26, layer);
	}

	@Override
	protected void drawForeground(DrawContext drawContext, final int mouseX, final int mouseY) {
		super.drawForeground(drawContext, mouseX, mouseY);
		final Layer layer = Layer.FOREGROUND;
		if (hideGuiElements()) {
			return;
		}

		builder.drawProgressBar(drawContext, this, blockEntity.getProgressScaled(100), 100, 76, 45,
				mouseX, mouseY, GuiBuilder.ProgressDirection.RIGHT, layer);
		builder.drawMultiEnergyBar(drawContext, this, 9, 19, (int) blockEntity.getEnergy(),
				(int) blockEntity.getMaxStoredPower(), mouseX, mouseY, 0, layer);

		final int x = 96;
		int y = 48;

		Text signal = Text.translatable("gui.techreborn.isotope_separator.signal", blockEntity.getDisplaySignal());
		drawText(drawContext, signal, x, y, theme.titleColor().rgba(), layer);
		y += 10;

		Text rpm = Text.translatable("gui.techreborn.isotope_separator.rpm",
				String.format("%.0f", blockEntity.getDisplayRpm() * 100.0));
		drawText(drawContext, rpm, x, y, theme.titleColor().rgba(), layer);
		y += 10;

		Text limit = Text.translatable("gui.techreborn.isotope_separator.limit",
				String.format("%.0f", blockEntity.getDisplayRpmLimit() * 100.0));
		drawText(drawContext, limit, x, y, theme.titleColor().rgba(), layer);

		if (blockEntity.isDisplayDegraded()) {
			drawText(drawContext, Text.translatable("gui.techreborn.isotope_separator.degraded"),
					x, y + 10, 0xFF5555, layer);
		}
	}
}
