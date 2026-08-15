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
import reborncore.client.gui.GuiBase;
import reborncore.client.gui.widget.GuiButtonExtended;
import reborncore.client.gui.GuiBuilder;
import reborncore.common.screen.BuiltScreenHandler;
import reborncore.common.screen.BuiltScreenHandlerProvider;
import techreborn.blockentity.machine.multiblock.JsonMultiblockMachineBlockEntity;
import techreborn.blockentity.machine.multiblock.LargeLatheBlockEntity;
import techreborn.blockentity.machine.multiblock.LargeWireMillBlockEntity;
import techreborn.blockentity.machine.multiblock.FurnaceProMaxBlockEntity;
import techreborn.blockentity.machine.multiblock.LargeOreCrusherBlockEntity;
import techreborn.blockentity.machine.multiblock.LargeExtractorBlockEntity;
import techreborn.blockentity.machine.multiblock.LargeCentrifugeBlockEntity;
import techreborn.blockentity.machine.multiblock.LargeElectrolyzerBlockEntity;
import techreborn.blockentity.machine.multiblock.LargeGreenhouseBlockEntity;
import techreborn.blockentity.machine.multiblock.LargeRanchBlockEntity;
import techreborn.blockentity.machine.multiblock.LargeGrinderBlockEntity;
import techreborn.blockentity.machine.multiblock.IndustrialAlloySmelterBlockEntity;

/**
 * Generic GUI for the "large" multiblock machines (Large Compressor, Large
 * Wire Mill, Large Grinder). Draws the slot backgrounds matching each
 * machine's {@code createScreenHandler} layout, plus the progress bar and the
 * multiblock hologram button.
 */
public class GuiLargeMachine<T extends JsonMultiblockMachineBlockEntity & BuiltScreenHandlerProvider> extends GuiBase<BuiltScreenHandler> {

	private final T blockEntity;

	public GuiLargeMachine(int syncID, final PlayerEntity player, T blockEntity) {
		super(player, blockEntity, blockEntity.createScreenHandler(syncID, player));
		this.blockEntity = blockEntity;
	}

	@Override
	protected void drawBackground(DrawContext drawContext, final float f, final int mouseX, final int mouseY) {
		super.drawBackground(drawContext, f, mouseX, mouseY);
		final GuiBase.Layer layer = Layer.BACKGROUND;

		drawSlot(drawContext, 8, 72, layer);

		if (blockEntity instanceof LargeWireMillBlockEntity || blockEntity instanceof LargeLatheBlockEntity
				|| blockEntity instanceof FurnaceProMaxBlockEntity || blockEntity instanceof LargeOreCrusherBlockEntity
				|| blockEntity instanceof LargeExtractorBlockEntity) {
			// 1 input + 1 output
			drawSlot(drawContext, 55, 45, layer);
			drawSlot(drawContext, 101, 45, layer);
		} else if (blockEntity instanceof LargeCentrifugeBlockEntity || blockEntity instanceof LargeElectrolyzerBlockEntity) {
			// 2 stacked inputs + 4 outputs (Large Centrifuge / Large Electrolyzer)
			drawSlot(drawContext, 55, 26, layer);
			drawSlot(drawContext, 55, 45, layer);
			drawSlot(drawContext, 100, 25, layer);
			drawSlot(drawContext, 120, 25, layer);
			drawSlot(drawContext, 100, 45, layer);
			drawSlot(drawContext, 120, 45, layer);
		} else if (blockEntity instanceof LargeGreenhouseBlockEntity || blockEntity instanceof LargeRanchBlockEntity) {
			// 1 input + 2 outputs (Large Greenhouse / Large Ranch)
			drawSlot(drawContext, 55, 45, layer);
			drawSlot(drawContext, 101, 45, layer);
			drawSlot(drawContext, 121, 45, layer);
		} else {
			// 2 stacked inputs + 1 output (Large Compressor / Large Grinder / Industrial Alloy Smelter)
			drawSlot(drawContext, 55, 26, layer);
			drawSlot(drawContext, 55, 45, layer);
			drawSlot(drawContext, 101, 45, layer);
		}

		if (blockEntity.isMultiblockValid()) {
			builder.drawHologramButton(drawContext, this, 6, 4, mouseX, mouseY, layer);
		}
	}

	@Override
	protected void drawForeground(DrawContext drawContext, final int mouseX, final int mouseY) {
		super.drawForeground(drawContext, mouseX, mouseY);
		final GuiBase.Layer layer = Layer.FOREGROUND;

		builder.drawProgressBar(drawContext, this, blockEntity.getProgressScaled(100), 100, getProgressBarX(), getProgressBarY(), mouseX, mouseY, GuiBuilder.ProgressDirection.RIGHT, layer);
		if (blockEntity.isMultiblockValid()) {
			addHologramButton(6, 4, 212, layer).clickHandler(this::onClick);
		} else {
			builder.drawMultiblockMissingBar(drawContext, this, layer);
			addHologramButton(76, 56, 212, layer).clickHandler(this::onClick);
			builder.drawHologramButton(drawContext, this, 76, 56, mouseX, mouseY, layer);
		}
		builder.drawMultiEnergyBar(drawContext, this, 9, 19, (int) blockEntity.getEnergy(), (int) blockEntity.getMaxStoredPower(), mouseX, mouseY, 0, layer);
	}

	public void onClick(GuiButtonExtended button, Double mouseX, Double mouseY) {
		blockEntity.renderMultiblock ^= !hideGuiElements();
	}

	/**
	 * Progress arrow x position: the "large" batch machines used to sit at
	 * (71, 40), which hugs the left edge of the input/output gap. Move the
	 * arrow right and down so it sits centered between the input and output
	 * columns (78, 44) or aligned with the small machines (76, 48).
	 */
	private int getProgressBarX() {
		if (blockEntity instanceof IndustrialAlloySmelterBlockEntity || blockEntity instanceof LargeCentrifugeBlockEntity
				|| blockEntity instanceof LargeElectrolyzerBlockEntity || blockEntity instanceof LargeGrinderBlockEntity
				|| blockEntity instanceof LargeWireMillBlockEntity) {
			return 78;
		}
		if (blockEntity instanceof LargeExtractorBlockEntity || blockEntity instanceof LargeGreenhouseBlockEntity
				|| blockEntity instanceof LargeRanchBlockEntity || blockEntity instanceof LargeOreCrusherBlockEntity
				|| blockEntity instanceof FurnaceProMaxBlockEntity) {
			return 76;
		}
		return 71;
	}

	private int getProgressBarY() {
		if (blockEntity instanceof IndustrialAlloySmelterBlockEntity || blockEntity instanceof LargeCentrifugeBlockEntity
				|| blockEntity instanceof LargeElectrolyzerBlockEntity || blockEntity instanceof LargeGrinderBlockEntity
				|| blockEntity instanceof LargeWireMillBlockEntity) {
			return 44;
		}
		if (blockEntity instanceof LargeExtractorBlockEntity || blockEntity instanceof LargeGreenhouseBlockEntity
				|| blockEntity instanceof LargeRanchBlockEntity || blockEntity instanceof LargeOreCrusherBlockEntity
				|| blockEntity instanceof FurnaceProMaxBlockEntity) {
			return 48;
		}
		return 40;
	}
}
