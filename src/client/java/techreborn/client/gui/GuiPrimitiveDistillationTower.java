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
import techreborn.blockentity.machine.multiblock.PrimitiveDistillationTowerBlockEntity;

/**
 * GUI of the Primitive Distillation Tower: the shared tower layout plus the two
 * cleaning slots and a fouling readout.
 */
public class GuiPrimitiveDistillationTower extends GuiDistillationTower<PrimitiveDistillationTowerBlockEntity> {

	private static final int BLOCKED_COLOUR = 0xFF5555;

	public GuiPrimitiveDistillationTower(int syncID, final PlayerEntity player,
			final PrimitiveDistillationTowerBlockEntity blockEntity) {
		super(syncID, player, blockEntity);
	}

	@Override
	protected void drawBackground(DrawContext drawContext, final float f, final int mouseX, final int mouseY) {
		super.drawBackground(drawContext, f, mouseX, mouseY);
		final Layer layer = Layer.BACKGROUND;

		// Cleaning solvent cell in, empty cell out
		drawSlot(drawContext, 89, 62, layer);
		drawSlot(drawContext, 107, 62, layer);
	}

	@Override
	protected void drawForeground(DrawContext drawContext, final int mouseX, final int mouseY) {
		super.drawForeground(drawContext, mouseX, mouseY);
		final Layer layer = Layer.FOREGROUND;

		int impurity = blockEntity.getImpurity();
		Text line = blockEntity.isBlocked()
				? Text.translatable("gui.techreborn.primitive_distillation_tower.blocked", impurity)
				: Text.translatable("gui.techreborn.primitive_distillation_tower.impurity", impurity);
		drawText(drawContext, line, 28, 80, blockEntity.isBlocked() ? BLOCKED_COLOUR : theme.titleColor().rgba(), layer);
	}
}
