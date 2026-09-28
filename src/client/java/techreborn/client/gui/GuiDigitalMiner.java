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

import java.util.List;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

import org.lwjgl.glfw.GLFW;

import reborncore.client.gui.GuiBase;
import reborncore.client.gui.widget.GuiButtonExtended;
import reborncore.client.gui.widget.GuiButtonHologram;
import reborncore.client.gui.widget.GuiButtonUpDown;
import reborncore.client.gui.widget.GuiButtonUpDown.UpDownButtonType;
import reborncore.common.screen.BuiltScreenHandler;

import techreborn.blockentity.machine.multiblock.DigitalMinerBlockEntity;
import techreborn.packets.serverbound.DigitalMinerPayload;

/**
 * GUI of the Digital Miner: the filter field, the range controls, eight output
 * slots and the "what will be mined" readout.
 * <p>
 * The screen is {@value DigitalMinerBlockEntity#EXTRA_HEIGHT}px taller than the
 * usual machine GUI. {@code GuiBase} derives the player inventory position from
 * {@code backgroundHeight} (see {@code GuiBase.getPlayerInventoryOffset()}), so
 * raising {@code backgroundHeight} here shifts the inventory down and it never
 * collides with the filter row, the range buttons, the readout or the preview.
 * {@code DigitalMinerBlockEntity.createScreenHandler} places the player slots
 * at the matching offset.
 * <p>
 * The filter is pushed to the server when the player presses Enter or the field
 * loses focus, so a half-typed expression never restarts the scan.
 */
public class GuiDigitalMiner extends GuiBase<BuiltScreenHandler> {

	/**
	 * Layout inside the machine area: the output slots and the energy slot are
	 * placed by the screen handler; these constants must match
	 * {@code DigitalMinerBlockEntity.createScreenHandler}.
	 *
	 * <pre>
	 *  y=6    machine title (centred)
	 *  y=18   filter field x=8..78 | "range" toggle x=82..156
	 *  y=34   "filter" label, on its own row below the field
	 *  y=47   range +/- buttons x=8..56
	 *  y=49   output slots 2x4 x=83..156 (two rows: 49 and 67)
	 *  y=76   energy slot, left column under the range buttons
	 *  y=84   targets / speed / range readout (left column, x&gt;=28)
	 *  y=114  preview of the matched blocks, 2 lines
	 *  y=133  player inventory (shifted down by DigitalMinerBlockEntity.EXTRA_HEIGHT)
	 * </pre>
	 */
	private static final int FILTER_X = 8;
	private static final int FILTER_Y = 18;
	private static final int FILTER_WIDTH = 70;
	private static final int FILTER_HEIGHT = 14;

	/** "Filter" label, on its own row directly below the filter field. */
	private static final int FILTER_LABEL_Y = FILTER_Y + FILTER_HEIGHT + 2;

	private static final int BUTTON_X = 8;
	private static final int BUTTON_Y = 47;

	/** "Range" toggle, sharing the filter row. */
	private static final int RANGE_BUTTON_X = 82;
	private static final int RANGE_BUTTON_Y = 18;
	private static final int RANGE_BUTTON_W = 74;
	private static final int RANGE_BUTTON_H = 14;

	private static final int OUTPUT_X = 84;
	private static final int OUTPUT_Y = 49;
	/** Below the range buttons, in the free left column. */
	private static final int ENERGY_SLOT_X = 8;
	private static final int ENERGY_SLOT_Y = 76;

	/** Right of the energy slot, below the output slots. */
	private static final int INFO_X = 28;
	private static final int INFO_Y = 84;
	private static final int INFO_LINE_HEIGHT = 10;

	private static final int PREVIEW_X = 8;
	private static final int PREVIEW_Y = 114;
	private static final int PREVIEW_LINE_HEIGHT = 9;
	/** Two lines fit between the readout and the player inventory at y=133. */
	private static final int PREVIEW_MAX_LINES = 2;

	private final DigitalMinerBlockEntity miner;
	private TextFieldWidget filterField;
	/** Last text handed to the server, to avoid redundant packets. */
	private String sentFilter;
	private boolean filterFocused;
	/** Hologram toggle; created once and re-positioned when the structure changes. */
	private GuiButtonHologram hologramButton;
	private boolean hologramValid;

	public GuiDigitalMiner(int syncID, final PlayerEntity player, DigitalMinerBlockEntity blockEntity) {
		super(player, blockEntity, blockEntity.createScreenHandler(syncID, player));
		this.miner = blockEntity;
		this.sentFilter = blockEntity.getFilterText();
		// GuiBase reads this for the background size and the inventory position
		this.backgroundHeight += DigitalMinerBlockEntity.EXTRA_HEIGHT;
	}

	@Override
	public void init() {
		super.init();
		int left = getGuiLeft();
		int top = getGuiTop();

		// range buttons
		addDrawableChild(new GuiButtonUpDown(left + BUTTON_X, top + BUTTON_Y, this,
				b -> sendButton(1), UpDownButtonType.FORWARD));
		addDrawableChild(new GuiButtonUpDown(left + BUTTON_X + 12, top + BUTTON_Y, this,
				b -> sendButton(-1), UpDownButtonType.REWIND));
		addDrawableChild(new GuiButtonUpDown(left + BUTTON_X + 24, top + BUTTON_Y, this,
				b -> sendButton(5), UpDownButtonType.FASTFORWARD));
		addDrawableChild(new GuiButtonUpDown(left + BUTTON_X + 36, top + BUTTON_Y, this,
				b -> sendButton(-5), UpDownButtonType.FASTREWIND));

		// working range display, mirroring the chunk loader's button
		addDrawableChild(
			ButtonWidget.builder(rangeButtonText(), button -> {
				button.setMessage(rangeButtonText());
				reborncore.client.ClientChunkManager.toggleLoadedChunks(miner.getPos());
			})
			.position(left + RANGE_BUTTON_X, top + RANGE_BUTTON_Y)
			.size(RANGE_BUTTON_W, RANGE_BUTTON_H)
			.build()
		);

		filterField = new TextFieldWidget(getTextRenderer(), left + FILTER_X, top + FILTER_Y,
				FILTER_WIDTH, FILTER_HEIGHT, Text.translatable("gui.techreborn.digital_miner.filter"));
		filterField.setMaxLength(128);
		filterField.setText(miner.getFilterText());
		addDrawableChild(filterField);

		// Hologram toggle: top-left when the structure is formed, centre of the
		// machine area (over the "structure missing" overlay) when it is not.
		// addHologramButton() adds the GUI origin itself, so pass offsets.
		hologramValid = miner.isMultiblockValid();
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
		miner.renderMultiblock ^= !hideGuiElements();
	}

	private Text rangeButtonText() {
		return Text.translatable("gui.techreborn.digital_miner.show_range_short");
	}

	private void sendButton(int delta) {
		ClientPlayNetworking.send(new DigitalMinerPayload(miner.getPos(), delta, ""));
	}

	private void sendFilter() {
		String value = filterField.getText();
		if (value.equals(sentFilter)) {
			return;
		}
		sentFilter = value;
		ClientPlayNetworking.send(new DigitalMinerPayload(miner.getPos(), 0, value));
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		// While the filter field has focus, the "open inventory" key (E by
		// default) must be consumed so the screen stays open: HandledScreen
		// closes on that key before the focused widget ever sees it. The
		// character itself is inserted through charTyped, so nothing is lost.
		if (filterField != null && filterField.isFocused() && client != null
				&& (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER)) == 0
				&& client.options.inventoryKey.matchesKey(keyCode, scanCode)) {
			return true;
		}
		// Enter commits the filter instead of closing the screen
		if (filterField != null && filterField.isFocused()
				&& (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
			sendFilter();
			filterField.setFocused(false);
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void render(DrawContext drawContext, int mouseX, int mouseY, float partialTicks) {
		// commit the filter when the field loses focus
		if (filterField != null) {
			if (filterFocused && !filterField.isFocused()) {
				sendFilter();
			}
			filterFocused = filterField.isFocused();
		}
		super.render(drawContext, mouseX, mouseY, partialTicks);
	}

	@Override
	protected void drawBackground(DrawContext drawContext, float partialTicks, int mouseX, int mouseY) {
		super.drawBackground(drawContext, partialTicks, mouseX, mouseY);
		final Layer layer = Layer.BACKGROUND;
		if (hideGuiElements()) {
			return;
		}

		// Every slot needs its own frame drawn; the player inventory ones are
		// handled by GuiBase. drawSlot paints the plain 18px inventory frame,
		// which is what the rest of the machine GUIs use for inputs and outputs
		// alike (drawOutputSlot would paint a 26px decorative frame).
		for (int i = 0; i < DigitalMinerBlockEntity.OUTPUT_SLOTS; i++) {
			int col = i % 4;
			int row = i / 4;
			drawSlot(drawContext, OUTPUT_X + col * 18, OUTPUT_Y + row * 18, layer);
		}
		drawSlot(drawContext, ENERGY_SLOT_X, ENERGY_SLOT_Y, layer);

		// the filter field sits over the background, draw its frame
		drawContext.fill(getGuiLeft() + FILTER_X - 1, getGuiTop() + FILTER_Y - 1,
				getGuiLeft() + FILTER_X + FILTER_WIDTH + 1, getGuiTop() + FILTER_Y + FILTER_HEIGHT + 1, 0xFF202020);
		drawContext.fill(getGuiLeft() + FILTER_X, getGuiTop() + FILTER_Y,
				getGuiLeft() + FILTER_X + FILTER_WIDTH, getGuiTop() + FILTER_Y + FILTER_HEIGHT, 0xFF000000);

		// The structure can change while the GUI is open: keep the hologram
		// toggle at the right spot and grey the machine area out when invalid.
		boolean valid = miner.isMultiblockValid();
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
	protected void drawForeground(DrawContext drawContext, int mouseX, int mouseY) {
		super.drawForeground(drawContext, mouseX, mouseY);
		if (hideGuiElements()) {
			return;
		}

		// The structure state can change while the GUI is open: keep the
		// hologram toggle in the right spot and grey the machine area out.
		if (!hologramValid) {
			builder.drawMultiblockMissingBar(drawContext, this, Layer.FOREGROUND);
			builder.drawHologramButton(drawContext, this, 76, 56, mouseX, mouseY, Layer.FOREGROUND);
		}

		drawText(drawContext, Text.translatable("gui.techreborn.digital_miner.filter_label"),
				FILTER_X, FILTER_LABEL_Y, theme.titleColor().rgba(), Layer.FOREGROUND);

		// left column: targets / speed / range
		Text targets = Text.translatable("gui.techreborn.digital_miner.targets", miner.getTargetCount());
		drawText(drawContext, targets, INFO_X, INFO_Y, theme.titleColor().rgba(), Layer.FOREGROUND);

		Text speed = Text.translatable("gui.techreborn.digital_miner.speed", miner.getSpeedTicks(),
				miner.overclockerCount());
		drawText(drawContext, speed, INFO_X, INFO_Y + INFO_LINE_HEIGHT, theme.titleColor().rgba(), Layer.FOREGROUND);

		Text range = Text.translatable("gui.techreborn.digital_miner.radius", miner.getRadius(),
				miner.getRadius() * 2 + 1);
		drawText(drawContext, range, INFO_X, INFO_Y + INFO_LINE_HEIGHT * 2, theme.titleColor().rgba(), Layer.FOREGROUND);

		// preview of the matched blocks, last lines of the machine area
		List<String> lines = String.valueOf(miner.getPreviewText()).lines()
				.limit(PREVIEW_MAX_LINES)
				.toList();
		int y = PREVIEW_Y;
		for (String line : lines) {
			drawText(drawContext, Text.literal(line), PREVIEW_X, y, 0xAAAAAA, Layer.FOREGROUND);
			y += PREVIEW_LINE_HEIGHT;
		}
	}

	/** Kept for symmetry with the other GUIs; the miner has no recipe tabs. */
	@Override
	public boolean tryAddUpgrades() {
		return true;
	}
}
