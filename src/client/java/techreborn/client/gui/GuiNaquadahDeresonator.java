/*
 * This file is part of TechReborn, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2020 TechReborn
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
import net.minecraft.util.Formatting;
import reborncore.client.gui.GuiBuilder;
import techreborn.blockentity.machine.multiblock.NaquadahDeresonatorBlockEntity;

public class GuiNaquadahDeresonator extends GuiMultiblockMachine {

	private final NaquadahDeresonatorBlockEntity blockEntity;

	public GuiNaquadahDeresonator(int syncID, final PlayerEntity player, final NaquadahDeresonatorBlockEntity blockEntity) {
		super(player, blockEntity, blockEntity.createScreenHandler(syncID, player));
		this.blockEntity = blockEntity;
	}

	@Override
	protected void drawBackground(DrawContext drawContext, final float f, final int mouseX, final int mouseY) {
		super.drawBackground(drawContext, f, mouseX, mouseY);
		final Layer layer = Layer.BACKGROUND;

		// Battery slot
		drawSlot(drawContext, 8, 72, layer);
		// Input slots
		drawSlot(drawContext, 35, 27, layer);
		drawSlot(drawContext, 35, 47, layer);
		// Output slots
		drawSlot(drawContext, 107, 27, layer);
		drawSlot(drawContext, 125, 27, layer);
		drawSlot(drawContext, 107, 47, layer);
		drawSlot(drawContext, 125, 47, layer);

		drawHologramButton(drawContext, mouseX, mouseY, layer);
	}

	@Override
	protected void drawForeground(DrawContext drawContext, final int mouseX, final int mouseY) {
		super.drawForeground(drawContext, mouseX, mouseY);
		final Layer layer = Layer.FOREGROUND;

		builder.drawProgressBar(drawContext, this, blockEntity.getProgressScaled(100), 100, 82, 37, mouseX, mouseY, GuiBuilder.ProgressDirection.RIGHT, layer);
		builder.drawTank(drawContext, this, 57, 12, mouseX, mouseY, blockEntity.tank.getFluidInstance(), blockEntity.tank.getFluidValueCapacity(), blockEntity.tank.isEmpty(), layer);

		// The chance the run in progress will actually produce its outputs.
		drawCentredText(drawContext, Text.translatable("gui.techreborn.naquadah_deresonator.chance",
						Text.literal(String.format("%.2f%%", blockEntity.getCurrentSuccessRate() * 100.0)).formatted(Formatting.YELLOW)),
				70, theme.titleColor().rgba(), layer);

		drawMissingStructureOverlay(drawContext, mouseX, mouseY, layer);
		builder.drawMultiEnergyBar(drawContext, this, 9, 19, (int) blockEntity.getEnergy(), (int) blockEntity.getMaxStoredPower(), mouseX, mouseY, 0, layer);
	}

}
