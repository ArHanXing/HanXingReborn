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
import reborncore.client.gui.GuiBase;
import reborncore.common.fluid.FluidUtils;
import reborncore.common.fluid.container.FluidInstance;
import reborncore.common.screen.BuiltScreenHandler;
import techreborn.blockentity.storage.fluid.WaterSinkBlockEntity;

/**
 * The water sink's GUI: a tank gauge, the fluid name and the amount, plus the
 * cell slot and its product slot. Deliberately sparser than the tank unit's GUI,
 * which has a slot pair per fluid tier that a sink does not need.
 */
public class GuiWaterSink extends GuiBase<BuiltScreenHandler> {

	private final WaterSinkBlockEntity blockEntity;

	public GuiWaterSink(int syncID, final PlayerEntity player, final WaterSinkBlockEntity blockEntity) {
		super(player, blockEntity, blockEntity.createScreenHandler(syncID, player));
		this.blockEntity = blockEntity;
	}

	@Override
	protected void drawBackground(DrawContext drawContext, final float f, final int mouseX, final int mouseY) {
		super.drawBackground(drawContext, f, mouseX, mouseY);
		final Layer layer = Layer.BACKGROUND;

		// Cell input and its emptied cell output.
		drawSlot(drawContext, 25, 35, layer);
		drawOutputSlot(drawContext, 25, 55, layer);
	}

	@Override
	protected void drawForeground(DrawContext drawContext, final int mouseX, final int mouseY) {
		super.drawForeground(drawContext, mouseX, mouseY);
		final Layer layer = Layer.FOREGROUND;

		builder.drawTank(drawContext, this, 44, 25, mouseX, mouseY,
			blockEntity.tank.getFluidInstance(), blockEntity.tank.getFluidValueCapacity(),
			blockEntity.tank.isEmpty(), layer);

		FluidInstance fluid = blockEntity.tank.getFluidInstance();
		if (fluid.isEmpty()) {
			drawText(drawContext, Text.translatable("techreborn.tooltip.unit.empty"), 70, 28,
				theme.titleColor().rgba(), layer);
		} else {
			drawText(drawContext, Text.translatable("gui.techreborn.tank.type"), 70, 20,
				theme.titleColor().rgba(), layer);
			drawText(drawContext, Text.literal(FluidUtils.getFluidName(fluid).replace("_", " ")), 70, 30,
				theme.titleColor().rgba(), layer);
			drawText(drawContext, Text.translatable("gui.techreborn.tank.amount"), 70, 44,
				theme.titleColor().rgba(), layer);
			drawText(drawContext, Text.literal(fluid.getAmount().toString()), 70, 54,
				theme.titleColor().rgba(), layer);
		}

		// The sink refills by itself, so say so rather than leaving the player to
		// wonder why the gauge never drops.
		drawText(drawContext, Text.translatable("gui.techreborn.water_sink.infinite"), 70, 72,
			theme.warningTextColor().rgba(), layer);
	}
}
