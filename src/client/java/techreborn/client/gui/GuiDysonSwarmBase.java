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
import reborncore.client.gui.widget.GuiButtonExtended;
import reborncore.common.screen.BuiltScreenHandler;
import reborncore.common.screen.BuiltScreenHandlerProvider;
import techreborn.blockentity.machine.multiblock.DysonSwarmMachineBlockEntity;

/**
 * Shared GUI of the Dyson Swarm machines (host and receiver): bound player
 * name/UUID/sail count, bind and unbind buttons and the multiblock hologram
 * button / missing-structure bar. Subclasses add machine specific parts (the
 * solar sail input slot of the host, the energy bar and EU/t line of the
 * receiver) and know which packet to send for the buttons.
 */
public abstract class GuiDysonSwarmBase<T extends DysonSwarmMachineBlockEntity & BuiltScreenHandlerProvider> extends GuiBase<BuiltScreenHandler> {

	private static final int BTN_WIDTH = 40;
	private static final int BTN_HEIGHT = 12;
	private static final int BTN_Y = 70;
	private static final int TEXT_Y_NAME = 18;
	private static final int TEXT_Y_UUID = 28;
	private static final int TEXT_Y_SAILS = 39;

	protected final T blockEntity;
	/** Left edge of the info text column (and of the bind button). */
	private final int textX;

	protected GuiDysonSwarmBase(int syncID, final PlayerEntity player, T blockEntity, int textX) {
		super(player, blockEntity, blockEntity.createScreenHandler(syncID, player));
		this.blockEntity = blockEntity;
		this.textX = textX;
	}

	/**
	 * Sends the bind ({@code true}) / unbind ({@code false}) button click to
	 * the server.
	 */
	protected abstract void sendBinding(boolean bind);

	/**
	 * Hook for extra GUI state (energy bar of the receiver, solar sail input
	 * slot of the host).
	 */
	protected void drawMachineBackground(DrawContext drawContext, int mouseX, int mouseY) {
	}

	@Override
	protected void drawBackground(DrawContext drawContext, final float f, final int mouseX, final int mouseY) {
		super.drawBackground(drawContext, f, mouseX, mouseY);
		drawMachineBackground(drawContext, mouseX, mouseY);
		if (blockEntity.isMultiblockValid()) {
			builder.drawHologramButton(drawContext, this, 6, 4, mouseX, mouseY, Layer.BACKGROUND);
		}
	}

	@Override
	protected void drawForeground(DrawContext drawContext, final int mouseX, final int mouseY) {
		super.drawForeground(drawContext, mouseX, mouseY);
		final Layer layer = Layer.FOREGROUND;

		boolean bound = !blockEntity.getBoundPlayerName().isEmpty();
		if (bound) {
			drawText(drawContext, Text.translatable("gui.techreborn.dyson.bound_to",
					Text.literal(blockEntity.getBoundPlayerName()).formatted(Formatting.YELLOW)),
					textX, TEXT_Y_NAME, 0x404040, layer);
			SpaceElevatorGuiUtils.drawScaledText(this, drawContext,
					Text.literal(blockEntity.getBoundPlayerUuidString()).formatted(Formatting.GRAY),
					textX, TEXT_Y_UUID, 0.5F, 0x404040, layer);
		} else {
			drawText(drawContext, Text.translatable("gui.techreborn.dyson.unbound").formatted(Formatting.GRAY),
					textX, TEXT_Y_NAME, 0x404040, layer);
		}

		drawText(drawContext, Text.translatable("gui.techreborn.dyson.sails",
				Text.literal(String.valueOf(blockEntity.getDisplaySailCount())).formatted(Formatting.YELLOW)),
				textX, TEXT_Y_SAILS, 0x404040, layer);
		drawMachineForeground(drawContext, layer, mouseX, mouseY);

		if (blockEntity.isMultiblockValid()) {
			addHologramButton(6, 4, 212, layer).clickHandler(this::onClick);
		} else {
			builder.drawMultiblockMissingBar(drawContext, this, layer);
			addHologramButton(76, 56, 212, layer).clickHandler(this::onClick);
			builder.drawHologramButton(drawContext, this, 76, 56, mouseX, mouseY, layer);
		}
	}

	/**
	 * Hook for extra foreground parts (energy bar and EU/t line of the
	 * receiver).
	 */
	protected void drawMachineForeground(DrawContext drawContext, Layer layer, int mouseX, int mouseY) {
	}

	@Override
	public void init() {
		super.init();
		GuiButtonExtended bindButton = new GuiButtonExtended(this.x + textX, this.y + BTN_Y, BTN_WIDTH, BTN_HEIGHT,
				Text.translatable("gui.techreborn.dyson.btn_bind"), button -> {
				}).clickHandler((button, mouseX, mouseY) -> sendBinding(true));
		GuiButtonExtended unbindButton = new GuiButtonExtended(this.x + textX + BTN_WIDTH + 4, this.y + BTN_Y,
				BTN_WIDTH, BTN_HEIGHT, Text.translatable("gui.techreborn.dyson.btn_unbind"), button -> {
				}).clickHandler((button, mouseX, mouseY) -> sendBinding(false));
		addDrawableChild(bindButton);
		addDrawableChild(unbindButton);
	}

	public void onClick(GuiButtonExtended button, Double mouseX, Double mouseY) {
		blockEntity.renderMultiblock ^= !hideGuiElements();
	}
}
