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

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import reborncore.client.gui.GuiBuilder;
import techreborn.blockentity.machine.multiblock.GasCollectorBlockEntity;

public class GuiGasCollector extends GuiMultiblockMachine {

	private final GasCollectorBlockEntity blockEntity;

	public GuiGasCollector(int syncID, final PlayerEntity player, final GasCollectorBlockEntity blockEntity) {
		super(player, blockEntity, blockEntity.createScreenHandler(syncID, player));
		this.blockEntity = blockEntity;
	}

	@Override
	protected void drawBackground(DrawContext drawContext, final float f, final int mouseX, final int mouseY) {
		super.drawBackground(drawContext, f, mouseX, mouseY);
		final Layer layer = Layer.BACKGROUND;

		// Battery slot
		drawSlot(drawContext, 8, 72, layer);
		// Empty container in
		drawSlot(drawContext, 124, 35, layer);
		// Filled container out
		drawSlot(drawContext, 124, 55, layer);

		drawHologramButton(drawContext, mouseX, mouseY, layer);
	}

	@Override
	protected void drawForeground(DrawContext drawContext, final int mouseX, final int mouseY) {
		super.drawForeground(drawContext, mouseX, mouseY);
		final Layer layer = Layer.FOREGROUND;

		builder.drawTank(drawContext, this, 99, 25, mouseX, mouseY, blockEntity.tank.getFluidInstance(), blockEntity.tank.getFluidValueCapacity(), blockEntity.tank.isEmpty(), layer);
		builder.drawProgressBar(drawContext, this, blockEntity.getProgressScaled(100), 100, 76, 48, mouseX, mouseY, GuiBuilder.ProgressDirection.RIGHT, layer);
		builder.drawMultiEnergyBar(drawContext, this, 9, 19, (int) blockEntity.getEnergy(), (int) blockEntity.getMaxStoredPower(), mouseX, mouseY, 0, layer);

		Identifier dimension = currentDimension();
		if (dimension != null) {
			drawText(drawContext, Text.translatable("gui.techreborn.gas_collector.dimension",
							Text.translatable("dimension." + dimension.getNamespace() + "." + dimension.getPath())),
					28, 80, theme.titleColor().rgba(), layer);
		}

		drawMissingStructureOverlay(drawContext, mouseX, mouseY, layer);
	}

	private static Identifier currentDimension() {
		MinecraftClient client = MinecraftClient.getInstance();
		return client.world == null ? null : client.world.getRegistryKey().getValue();
	}
}
