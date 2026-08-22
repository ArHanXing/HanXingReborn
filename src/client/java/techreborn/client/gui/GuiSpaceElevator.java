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
import net.minecraft.util.Formatting;
import reborncore.client.gui.GuiBase;
import reborncore.client.gui.GuiBuilder;
import reborncore.client.gui.widget.GuiButtonExtended;
import reborncore.common.screen.BuiltScreenHandler;
import reborncore.common.screen.BuiltScreenHandlerProvider;
import techreborn.blockentity.machine.multiblock.SpaceElevatorBlockEntity;

/**
 * Read-only GUI of the Space Elevator host: energy bar, multiblock hologram
 * button / missing-structure bar and a summary of the bound assembler and
 * miner units (kept alive by the host's periodic module refresh).
 */
public class GuiSpaceElevator<T extends SpaceElevatorBlockEntity & BuiltScreenHandlerProvider> extends GuiBase<BuiltScreenHandler> {

	private final T blockEntity;

	public GuiSpaceElevator(int syncID, final PlayerEntity player, T blockEntity) {
		super(player, blockEntity, blockEntity.createScreenHandler(syncID, player));
		this.blockEntity = blockEntity;
	}

	@Override
	protected void drawBackground(DrawContext drawContext, final float f, final int mouseX, final int mouseY) {
		super.drawBackground(drawContext, f, mouseX, mouseY);
		final GuiBase.Layer layer = Layer.BACKGROUND;

		if (blockEntity.isMultiblockValid()) {
			builder.drawHologramButton(drawContext, this, 6, 4, mouseX, mouseY, layer);
		}
	}

	@Override
	protected void drawForeground(DrawContext drawContext, final int mouseX, final int mouseY) {
		super.drawForeground(drawContext, mouseX, mouseY);
		final GuiBase.Layer layer = Layer.FOREGROUND;

		builder.drawMultiEnergyBar(drawContext, this, 130, 28, (int) blockEntity.getEnergy(), (int) blockEntity.getMaxStoredPower(), mouseX, mouseY, 0, layer);

		// Bound module list, synced from the server as one "A x y z" (assembler)
		// or "M x y z" (miner) line per unit. Drawn scaled-down in the top-left
		// area so it never overlaps the energy bar on the right.
		String summary = blockEntity.getModuleSummary();
		String[] lines = summary.isEmpty() ? new String[0] : summary.split("\n");
		int count = lines.length;
		SpaceElevatorGuiUtils.drawScaledText(this, drawContext,
				Text.translatable("gui.techreborn.space_elevator.modules",
						Text.literal(count + "/" + SpaceElevatorBlockEntity.MAX_MODULES).formatted(Formatting.YELLOW)),
				8, 24, 0.7F, 0x404040, layer);
		int y = 33;
		for (String line : lines) {
			if (y > 88) {
				break; // keep within the left area of the gui
			}
			String[] parts = line.split(" ");
			if (parts.length >= 4) {
				boolean assembler = parts[0].equals("A");
				SpaceElevatorGuiUtils.drawScaledText(this, drawContext,
						Text.translatable(assembler ? "gui.techreborn.space_elevator.unit_assembler"
								: "gui.techreborn.space_elevator.unit_miner",
								parts[1], parts[2], parts[3]),
						8, y, 0.7F, 0x404040, layer);
			}
			y += 7;
		}

		if (blockEntity.isMultiblockValid()) {
			addHologramButton(6, 4, 212, layer).clickHandler(this::onClick);
		} else {
			builder.drawMultiblockMissingBar(drawContext, this, layer);
			addHologramButton(76, 56, 212, layer).clickHandler(this::onClick);
			builder.drawHologramButton(drawContext, this, 76, 56, mouseX, mouseY, layer);
		}
	}

	public void onClick(GuiButtonExtended button, Double mouseX, Double mouseY) {
		blockEntity.renderMultiblock ^= !hideGuiElements();
	}
}
