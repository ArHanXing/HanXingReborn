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

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import reborncore.client.gui.GuiBase;
import reborncore.client.gui.GuiBuilder;
import reborncore.client.gui.widget.GuiButtonExtended;
import reborncore.client.gui.widget.GuiButtonHologram;
import reborncore.common.screen.BuiltScreenHandler;

import techreborn.blockentity.machine.multiblock.IsotopeSeparatorBlockEntity;

/**
 * GUI of the Isotope Separator.
 * <p>
 * On top of the usual slots and bars it shows the numbers that drive the
 * machine: the incoming redstone signal, the resulting rotation speed, the
 * rotor's speed cap and a red warning once the speed passes the point where the
 * separation starts losing a grade. All of those are computed live from the
 * world and the (synced) rotor slot, so the readout follows the redstone signal
 * immediately instead of waiting for a block-entity sync.
 * <p>
 * The rotor slot is the heart of the machine, so it is outlined and labelled,
 * and hovering it shows the rotor's speed cap, durability and estimated
 * remaining life at the current speed.
 */
public class GuiIsotopeSeparator extends GuiBase<BuiltScreenHandler> {

	private static final int INPUT_SLOT_X = 35;
	private static final int INPUT_SLOT_Y = 35;
	private static final int ROTOR_SLOT_X = 55;
	private static final int ROTOR_SLOT_Y = 35;
	private static final int PRODUCT_SLOT_X = 107;
	private static final int PRODUCT_SLOT_Y = 26;
	private static final int TAILS_SLOT_X = 125;
	private static final int TAILS_SLOT_Y = 26;
	private static final int ENERGY_SLOT_X = 8;
	private static final int ENERGY_SLOT_Y = 72;

	/** Label under the rotor slot, marking it as the rotor slot. */
	private static final int ROTOR_LABEL_X = ROTOR_SLOT_X;
	private static final int ROTOR_LABEL_Y = ROTOR_SLOT_Y + 20;

	final IsotopeSeparatorBlockEntity blockEntity;

	/** Hologram toggle; created once and re-positioned when the structure changes. */
	private GuiButtonHologram hologramButton;
	private boolean hologramValid;

	public GuiIsotopeSeparator(int syncID, final PlayerEntity player, final IsotopeSeparatorBlockEntity blockEntity) {
		super(player, blockEntity, blockEntity.createScreenHandler(syncID, player));
		this.blockEntity = blockEntity;
	}

	@Override
	public void init() {
		super.init();
		// Hologram toggle: top-left when the structure is formed, centre of the
		// machine area (over the "structure missing" overlay) when it is not.
		// addHologramButton() adds the GUI origin itself, so pass offsets.
		hologramValid = blockEntity.isMultiblockValid();
		GuiButtonHologram button = addHologramButton(hologramOffsetX(), hologramOffsetY(), 212, Layer.FOREGROUND);
		button.clickHandler(this::onClick);
		hologramButton = button;
	}

	/** X of the hologram toggle, relative to the GUI origin. */
	private int hologramOffsetX() {
		return hologramValid ? 6 : 76;
	}

	/** Y of the hologram toggle, relative to the GUI origin. */
	private int hologramOffsetY() {
		return hologramValid ? 4 : 56;
	}

	/** Toggles the multiblock hologram, like the other multiblock machines. */
	public void onClick(GuiButtonExtended button, Double mouseX, Double mouseY) {
		blockEntity.renderMultiblock ^= !hideGuiElements();
	}

	@Override
	protected void drawBackground(DrawContext drawContext, final float f, final int mouseX, final int mouseY) {
		super.drawBackground(drawContext, f, mouseX, mouseY);
		final Layer layer = Layer.BACKGROUND;

		// energy
		drawSlot(drawContext, ENERGY_SLOT_X, ENERGY_SLOT_Y, layer);
		// feed and rotor
		drawSlot(drawContext, INPUT_SLOT_X, INPUT_SLOT_Y, layer);
		drawSlot(drawContext, ROTOR_SLOT_X, ROTOR_SLOT_Y, layer);
		// product and tails
		drawSlot(drawContext, PRODUCT_SLOT_X, PRODUCT_SLOT_Y, layer);
		drawSlot(drawContext, TAILS_SLOT_X, TAILS_SLOT_Y, layer);

		// Outline the rotor slot: the whole machine is built around it.
		drawContext.drawBorder(getGuiLeft() + ROTOR_SLOT_X - 2, getGuiTop() + ROTOR_SLOT_Y - 2, 22, 22,
				theme.titleColor().rgba());

		// The structure can change while the GUI is open: keep the hologram
		// toggle at the right spot and grey the machine area out when invalid.
		boolean valid = blockEntity.isMultiblockValid();
		if (valid != hologramValid) {
			hologramValid = valid;
			// setX/setY take absolute screen coordinates
			hologramButton.setX(hologramOffsetX() + getGuiLeft());
			hologramButton.setY(hologramOffsetY() + getGuiTop());
		}
		if (valid) {
			builder.drawHologramButton(drawContext, this, 6, 4, mouseX, mouseY, layer);
		}
	}

	@Override
	protected void drawForeground(DrawContext drawContext, final int mouseX, final int mouseY) {
		super.drawForeground(drawContext, mouseX, mouseY);
		final Layer layer = Layer.FOREGROUND;
		if (hideGuiElements()) {
			return;
		}

		if (!hologramValid) {
			builder.drawMultiblockMissingBar(drawContext, this, layer);
			builder.drawHologramButton(drawContext, this, 76, 56, mouseX, mouseY, layer);
		}

		builder.drawProgressBar(drawContext, this, blockEntity.getProgressScaled(100), 100, 76, 45,
				mouseX, mouseY, GuiBuilder.ProgressDirection.RIGHT, layer);
		builder.drawMultiEnergyBar(drawContext, this, 9, 19, (int) blockEntity.getEnergy(),
				(int) blockEntity.getMaxStoredPower(), mouseX, mouseY, 0, layer);

		// Live values: the rotation speed is a pure function of the redstone
		// signal at the controller and the rotor in the (synced) slot, so the
		// client can compute exactly what the server is running at.
		final int signal = blockEntity.getRedstoneSignal();
		final double rpm = blockEntity.getRpm();

		final int x = 96;
		int y = 48;

		drawText(drawContext, Text.translatable("gui.techreborn.isotope_separator.signal", signal),
				x, y, theme.titleColor().rgba(), layer);
		y += 10;

		drawText(drawContext, Text.translatable("gui.techreborn.isotope_separator.rpm",
						String.format("%.0f", rpm * 100.0)),
				x, y, theme.titleColor().rgba(), layer);
		y += 10;

		drawText(drawContext, Text.translatable("gui.techreborn.isotope_separator.limit",
						String.format("%.0f", blockEntity.getRotorRpmLimit() * 100.0)),
				x, y, theme.titleColor().rgba(), layer);

		if (blockEntity.isDegraded()) {
			drawText(drawContext, Text.translatable("gui.techreborn.isotope_separator.degraded"),
					x, y + 10, 0xFF5555, layer);
		}

		// Mark the rotor slot with a label under it.
		drawText(drawContext, Text.translatable("gui.techreborn.isotope_separator.rotor_slot"),
				ROTOR_LABEL_X, ROTOR_LABEL_Y, theme.titleColor().rgba(), layer);
	}

	/**
	 * Replaces the plain item tooltip of the rotor slot with a rotor-specific
	 * one (speed cap, durability, estimated remaining life), and shows a hint
	 * when the slot is empty.
	 */
	@Override
	protected void drawMouseoverTooltip(DrawContext drawContext, int mouseX, int mouseY) {
		if (!hideGuiElements()
				&& isPointInRect(ROTOR_SLOT_X - 1, ROTOR_SLOT_Y - 1, 18, 18, mouseX, mouseY)) {
			drawContext.drawTooltip(getTextRenderer(), rotorTooltip(), mouseX, mouseY);
			return;
		}
		super.drawMouseoverTooltip(drawContext, mouseX, mouseY);
	}

	private List<Text> rotorTooltip() {
		List<Text> lines = new ArrayList<>();
		ItemStack rotor = blockEntity.getRotorStack();
		if (rotor.isEmpty()) {
			lines.add(Text.translatable("gui.techreborn.isotope_separator.rotor_none").formatted(Formatting.YELLOW));
			lines.add(Text.translatable("gui.techreborn.isotope_separator.rotor_hint").formatted(Formatting.GRAY));
			return lines;
		}
		lines.add(rotor.getName().copy().formatted(Formatting.YELLOW));
		lines.add(Text.translatable("gui.techreborn.isotope_separator.rotor_limit",
				String.format("%.0f", blockEntity.getRotorRpmLimit() * 100.0)).formatted(Formatting.GRAY));
		lines.add(Text.translatable("gui.techreborn.isotope_separator.rotor_durability",
				String.valueOf(rotor.getMaxDamage() - rotor.getDamage()),
				String.valueOf(rotor.getMaxDamage())).formatted(Formatting.GRAY));
		long remaining = blockEntity.getRotorRemainingTicks();
		if (remaining >= 0) {
			lines.add(Text.translatable("gui.techreborn.isotope_separator.rotor_remaining",
					IsotopeSeparatorBlockEntity.formatTicks(remaining)).formatted(Formatting.GRAY));
		}
		return lines;
	}
}
