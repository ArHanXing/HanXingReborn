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

import reborncore.client.gui.GuiBase;
import reborncore.client.gui.widget.GuiButtonUpDown;
import reborncore.client.gui.widget.GuiButtonUpDown.UpDownButtonType;
import reborncore.common.screen.BuiltScreenHandler;

import techreborn.blockentity.machine.multiblock.DigitalMinerBlockEntity;
import techreborn.packets.serverbound.DigitalMinerPayload;

/**
 * GUI of the Digital Miner: the filter field, the range controls, eight output
 * slots and the "what will be mined" readout.
 * <p>
 * The screen is 40px taller than the usual 176x176 machine GUI so the filter
 * field can sit on its own row at the top without colliding with the range
 * buttons and the readout. {@code GuiBase} derives both the background size and
 * the player inventory position from {@code backgroundHeight}, so raising it
 * here shifts everything below the machine area down consistently.
 * <p>
 * The filter is pushed to the server when the player presses Enter or the field
 * loses focus, so a half-typed expression never restarts the scan.
 */
public class GuiDigitalMiner extends GuiBase<BuiltScreenHandler> {

	/** Extra height over the standard 176, see the class comment. */
	private static final int EXTRA_HEIGHT = 78;

	/**
	 * Layout inside the machine area. The area is {@code backgroundHeight - 93}
	 * tall, i.e. 0..160, and the player inventory starts at y=173. The output
	 * slots and the energy slot are placed by the screen handler; these
	 * constants must match. Note that {@code drawSlot(x, y)} paints its frame at
	 * {@code (x-1, y-1)} and is about 20px wide, so the boxes below describe the
	 * drawn extents, not the 18px slot cells.
	 *
	 * <pre>
	 *  y=9   filter label
	 *  y=19  filter field, full width
	 *  y=41  range buttons x=8..56 | range toggle x=136..156 | outputs x=84..176
	 *  y=60  output row 2
	 *  y=95  targets / speed / range readout
	 *  y=116 preview of the matched blocks, up to 4 lines
	 * </pre>
	 */
	private static final int FILTER_LABEL_Y = 9;
	private static final int FILTER_X = 8;
	private static final int FILTER_Y = 19;
	private static final int FILTER_WIDTH = 130;
	private static final int FILTER_HEIGHT = 14;

	private static final int BUTTON_X = 8;
	private static final int BUTTON_Y = 41;

	/**
	 * "Range" toggle. The output slot frames fill x=83..176, so this sits on the
	 * filter row instead, where the field leaves room to its right.
	 */
	private static final int RANGE_BUTTON_X = 142;
	private static final int RANGE_BUTTON_Y = 19;
	private static final int RANGE_BUTTON_W = 26;
	private static final int RANGE_BUTTON_H = 14;

	private static final int OUTPUT_X = 84;
	private static final int OUTPUT_Y = 41;
	/** Below the output frames (which end at y=78). */
	private static final int ENERGY_SLOT_X = 156;
	private static final int ENERGY_SLOT_Y = 82;

	private static final int INFO_X = 8;
	private static final int INFO_Y = 84;
	private static final int INFO_LINE_HEIGHT = 10;

	private static final int PREVIEW_X = 8;
	private static final int PREVIEW_Y = 116;
	private static final int PREVIEW_LINE_HEIGHT = 9;
	/** Four lines fit between the readout and the player inventory at y=173. */
	private static final int PREVIEW_MAX_LINES = 4;

	private final DigitalMinerBlockEntity miner;
	private TextFieldWidget filterField;
	/** Last text handed to the server, to avoid redundant packets. */
	private String sentFilter;
	private boolean filterFocused;

	public GuiDigitalMiner(int syncID, final PlayerEntity player, DigitalMinerBlockEntity blockEntity) {
		super(player, blockEntity, blockEntity.createScreenHandler(syncID, player));
		this.miner = blockEntity;
		this.sentFilter = blockEntity.getFilterText();
		// GuiBase reads this for the background size and the inventory position
		this.backgroundHeight += EXTRA_HEIGHT;
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
		// Enter commits the filter instead of closing the screen
		if (filterField != null && filterField.isFocused()
				&& (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER)) {
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
		// handled by GuiBase.
		for (int i = 0; i < DigitalMinerBlockEntity.OUTPUT_SLOTS; i++) {
			int col = i % 4;
			int row = i / 4;
			drawOutputSlot(drawContext, OUTPUT_X + col * 18, OUTPUT_Y + row * 18, layer);
		}
		drawSlot(drawContext, ENERGY_SLOT_X, ENERGY_SLOT_Y, layer);

		// the filter field sits over the background, draw its frame
		drawContext.fill(getGuiLeft() + FILTER_X - 1, getGuiTop() + FILTER_Y - 1,
				getGuiLeft() + FILTER_X + FILTER_WIDTH + 1, getGuiTop() + FILTER_Y + FILTER_HEIGHT + 1, 0xFF202020);
		drawContext.fill(getGuiLeft() + FILTER_X, getGuiTop() + FILTER_Y,
				getGuiLeft() + FILTER_X + FILTER_WIDTH, getGuiTop() + FILTER_Y + FILTER_HEIGHT, 0xFF000000);
	}

	@Override
	protected void drawForeground(DrawContext drawContext, int mouseX, int mouseY) {
		super.drawForeground(drawContext, mouseX, mouseY);
		if (hideGuiElements()) {
			return;
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
