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
import reborncore.client.gui.GuiSprites;
import reborncore.client.gui.widget.GuiButtonUpDown;
import reborncore.client.gui.widget.GuiButtonUpDown.UpDownButtonType;
import reborncore.common.screen.BuiltScreenHandler;

import techreborn.blockentity.machine.multiblock.DigitalMinerBlockEntity;
import techreborn.config.TechRebornConfig;
import techreborn.packets.serverbound.DigitalMinerPayload;

/**
 * GUI of the Digital Miner: eight output slots, the range buttons, the filter
 * text field and the "what will be mined" information block.
 * <p>
 * The filter is pushed to the server when the player presses Enter or the field
 * loses focus, so a half-typed expression never restarts the scan.
 */
public class GuiDigitalMiner extends GuiBase<BuiltScreenHandler> {

	private static final int SLOT_SIZE = 18;
	/** Position of the first output slot. */
	private static final int OUTPUT_X = 71;
	private static final int OUTPUT_Y = 17;
	private static final int ENERGY_SLOT_X = 8;
	private static final int ENERGY_SLOT_Y = 42;

	private static final int FILTER_X = 8;
	private static final int FILTER_Y = 15;
	private static final int FILTER_WIDTH = 54;
	private static final int FILTER_HEIGHT = 14;

	private static final int INFO_X = 8;
	private static final int INFO_Y = 34;
	private static final int PREVIEW_Y = 58;

	private final DigitalMinerBlockEntity miner;
	private TextFieldWidget filterField;
	/** Last text handed to the server, to avoid redundant packets. */
	private String sentFilter;
	private boolean filterFocused;

	public GuiDigitalMiner(int syncID, final PlayerEntity player, DigitalMinerBlockEntity blockEntity) {
		super(player, blockEntity, blockEntity.createScreenHandler(syncID, player));
		this.miner = blockEntity;
		this.sentFilter = blockEntity.getFilterText();
	}

	@Override
	public void init() {
		super.init();
		int left = getGuiLeft();
		int top = getGuiTop();

		// range buttons
		addDrawableChild(new GuiButtonUpDown(left + 8, top + FILTER_Y + FILTER_HEIGHT + 2, this,
				b -> sendButton(1), UpDownButtonType.FORWARD));
		addDrawableChild(new GuiButtonUpDown(left + 8 + 12, top + FILTER_Y + FILTER_HEIGHT + 2, this,
				b -> sendButton(-1), UpDownButtonType.REWIND));
		addDrawableChild(new GuiButtonUpDown(left + 8 + 24, top + FILTER_Y + FILTER_HEIGHT + 2, this,
				b -> sendButton(5), UpDownButtonType.FASTFORWARD));
		addDrawableChild(new GuiButtonUpDown(left + 8 + 36, top + FILTER_Y + FILTER_HEIGHT + 2, this,
				b -> sendButton(-5), UpDownButtonType.FASTREWIND));

		// working range display, mirroring the chunk loader's button
		addDrawableChild(
			ButtonWidget.builder(rangeButtonText(), button -> {
				button.setMessage(rangeButtonText());
				reborncore.client.ClientChunkManager.toggleLoadedChunks(miner.getPos());
			})
			.position(left + 8, top + 74)
			.size(102, 14)
			.build()
		);

		filterField = new TextFieldWidget(getTextRenderer(), left + FILTER_X, top + FILTER_Y,
				FILTER_WIDTH, FILTER_HEIGHT, Text.translatable("gui.techreborn.digital_miner.filter"));
		filterField.setMaxLength(128);
		filterField.setText(miner.getFilterText());
		filterField.setChangedListener(value -> filterField.setEditableColor(0xFFFFFF));
		addDrawableChild(filterField);
	}

	private Text rangeButtonText() {
		return Text.translatable("gui.techreborn.digital_miner.show_range");
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
	protected void drawForeground(DrawContext drawContext, int mouseX, int mouseY) {
		super.drawForeground(drawContext, mouseX, mouseY);
		if (hideGuiElements()) {
			return;
		}

		drawText(drawContext, Text.translatable("gui.techreborn.digital_miner.filter_label"),
				FILTER_X, FILTER_Y - 10, theme.titleColor().rgba(), Layer.FOREGROUND);

		Text range = Text.translatable("gui.techreborn.digital_miner.radius", miner.getRadius(),
				miner.getRadius() * 2 + 1);
		drawText(drawContext, range, INFO_X, INFO_Y, theme.titleColor().rgba(), Layer.FOREGROUND);

		Text targets = Text.translatable("gui.techreborn.digital_miner.targets", miner.getTargetCount());
		drawText(drawContext, targets, INFO_X, INFO_Y + 10, theme.titleColor().rgba(), Layer.FOREGROUND);

		Text speed = Text.translatable("gui.techreborn.digital_miner.speed", miner.getSpeedTicks(),
				miner.overclockerCount());
		drawText(drawContext, speed, INFO_X, INFO_Y + 20, theme.titleColor().rgba(), Layer.FOREGROUND);

		// preview of the first few matched blocks
		drawText(drawContext, Text.translatable("gui.techreborn.digital_miner.preview"),
				INFO_X, PREVIEW_Y - 10, theme.titleColor().rgba(), Layer.FOREGROUND);
		List<String> lines = String.valueOf(miner.getPreviewText()).lines()
				.limit(Math.max(1, TechRebornConfig.digitalMinerMaxPreviewEntries))
				.toList();
		int y = PREVIEW_Y;
		for (String line : lines) {
			drawText(drawContext, Text.literal(line), INFO_X, y, 0xAAAAAA, Layer.FOREGROUND);
			y += 9;
		}
	}

	@Override
	protected void drawBackground(DrawContext drawContext, float partialTicks, int mouseX, int mouseY) {
		super.drawBackground(drawContext, partialTicks, mouseX, mouseY);
		final Layer layer = Layer.BACKGROUND;
		if (hideGuiElements()) {
			return;
		}
		// energy slot frame
		GuiSprites.drawSprite(drawContext, GuiSprites.SLOT, getGuiLeft() + ENERGY_SLOT_X, getGuiTop() + ENERGY_SLOT_Y);

		// the filter field sits over the background, draw its frame
		drawContext.fill(getGuiLeft() + FILTER_X - 1, getGuiTop() + FILTER_Y - 1,
				getGuiLeft() + FILTER_X + FILTER_WIDTH + 1, getGuiTop() + FILTER_Y + FILTER_HEIGHT + 1, 0xFF202020);
		drawContext.fill(getGuiLeft() + FILTER_X, getGuiTop() + FILTER_Y,
				getGuiLeft() + FILTER_X + FILTER_WIDTH, getGuiTop() + FILTER_Y + FILTER_HEIGHT, 0xFF000000);
	}

	/** Kept for symmetry with the other GUIs; the miner has no recipe tabs. */
	@Override
	public boolean tryAddUpgrades() {
		return true;
	}
}
