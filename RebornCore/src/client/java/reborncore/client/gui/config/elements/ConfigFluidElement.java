/*
 * This file is part of RebornCore, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021 TeamReborn
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

package reborncore.client.gui.config.elements;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import reborncore.client.gui.GuiBase;
import reborncore.client.gui.GuiSprites;
import reborncore.common.blockentity.FluidConfiguration;
import reborncore.common.blockentity.RedstoneConfiguration;
import reborncore.common.network.serverbound.SetRedstoneStatePayload;

/**
 * The fluid IO panel.
 * <p>
 * Tanks are picked before they are configured, the same way the item side makes
 * you pick a slot first: the tank selector ("储罐选择器") at the top of the panel
 * chooses which tank the face map and the switches below apply to. A machine
 * with a single tank shows a single entry, so the extra step costs nothing there.
 */
public class ConfigFluidElement extends ParentElement {
	private final SlotType type;

	/** Which tank the face map and switches below are editing. */
	private int selectedTank = 0;

	public ConfigFluidElement(SlotType type, int x, int y, GuiBase<?> gui) {
		super(x, y, type.getButtonSprite(), type.getTextureWidth(), type.getTextureHeight());
		this.type = type;

		FluidConfigPopupElement popupElement;

		elements.add(popupElement = new FluidConfigPopupElement(x - 22, y + 4, getHeight(), this));
		elements.add(new ButtonElement(x + 37, y - 25, GuiSprites.EXIT_BUTTON, 13, 13, gui::closeSelectedTab));

		// The tank selector: one entry per configurable tank, stacked vertically
		// on the left edge so a machine with several tanks does not push the row
		// off the panel.
		int tankCount = Math.max(1, gui.getMachine().getTankCount());
		for (int i = 0; i < tankCount; i++) {
			final int tankID = i;
			elements.add(new ButtonElement(x - 6, y + 104 + i * 15, GuiSprites.EXIT_BUTTON, 13, 13,
				() -> selectTank(tankID, gui)));
		}

		elements.add(new CheckBoxElement(Text.translatable("reborncore.gui.fluidconfig.pullin"), x - 26, y + 59,
			checkBoxElement -> holder(gui).autoInput(),
			() -> popupElement.updateCheckBox("input", gui)));
		elements.add(new CheckBoxElement(Text.translatable("reborncore.gui.fluidconfig.pumpout"), x - 26, y + 74,
			checkBoxElement -> holder(gui).autoOutput(),
			() -> popupElement.updateCheckBox("output", gui)));

		// The redstone gate that decides whether fluid IO runs at all used to sit
		// on its own page, splitting one concern across two screens. It belongs to
		// this panel: the tank selector picks *which* tank, this decides whether
		// its IO is live at all.
		if (gui.getMachine().getRedstoneElements().contains(RedstoneConfiguration.Element.FLUID_IO)) {
			elements.add(new ButtonElement(x - 26, y + 150, GuiSprites.EXIT_BUTTON, 13, 13,
				() -> cycleFluidGate(gui)));
		}
	}

	/**
	 * Cycles the redstone gate that enables fluid IO, the same way the redstone
	 * page does.
	 *
	 * @param gui {@link GuiBase} the open GUI
	 */
	private void cycleFluidGate(GuiBase<?> gui) {
		RedstoneConfiguration configuration = gui.getMachine().getRedstoneConfiguration();
		RedstoneConfiguration.State current = configuration.getState(RedstoneConfiguration.Element.FLUID_IO);
		int next = current.ordinal() + 1;
		if (next >= RedstoneConfiguration.State.values().length) {
			next = 0;
		}
		ClientPlayNetworking.send(new SetRedstoneStatePayload(gui.be.getPos(),
			RedstoneConfiguration.Element.FLUID_IO, RedstoneConfiguration.State.values()[next]));
	}

	/**
	 * @param gui {@link GuiBase} the open GUI
	 * @return {@link FluidConfiguration.TankConfigHolder} the holder being edited
	 */
	private FluidConfiguration.TankConfigHolder holder(GuiBase<?> gui) {
		FluidConfiguration configuration = gui.getMachine().fluidConfiguration;
		FluidConfiguration.TankConfigHolder holder = configuration.getTankDetails(selectedTank);
		if (holder == null) {
			// The server has not sent this tank yet; fall back to tank 0 rather
			// than dereferencing null in the middle of a draw call.
			holder = configuration.getTankDetails(0);
		}
		return holder;
	}

	/**
	 * @return {@code int} the tank index currently being configured
	 */
	public int getSelectedTank() {
		return selectedTank;
	}

	private void selectTank(int tankID, GuiBase<?> gui) {
		if (gui.getMachine().fluidConfiguration.getTankDetails(tankID) == null) {
			return;
		}
		selectedTank = tankID;
	}

	@Override
	public int getWidth() {
		// Wide enough for the tank selector column on the left plus the face grid.
		return 110;
	}

	@Override
	public int getHeight() {
		// Taller than the old 122: the panel now carries the tank selector, the
		// face grid, both switches and the fluid IO gate, and at 122 the last two
		// rows ran past the background.
		return 178;
	}

	@Override
	public void draw(DrawContext drawContext, GuiBase<?> gui, int mouseX, int mouseY) {
		if (isMouseWithinRect(gui, mouseX, mouseY)) {
			drawSprite(drawContext, gui, type.getButtonHoverOverlay(), getX(), getY(), type.getTextureWidth(), type.getTextureHeight());
		}
		super.draw(drawContext, gui, mouseX, mouseY);

		// The coordinates here are GUI-relative: drawText and drawSprite add the
		// GUI origin themselves. Adding it here as well shifted the labels away
		// from the panel and left the buttons' hit boxes behind.
		// The selector lives inside the panel, under the switches, so it cannot
		// spill outside the background box the way it did when it sat at x-30.
		drawText(drawContext, gui, Text.translatable("reborncore.gui.fluidconfig.tank_selector"),
			getX() - 6, getY() + 96, 0xFFFFFFFF);
		drawText(drawContext, gui, Text.translatable("reborncore.gui.fluidconfig.tank_current", selectedTank + 1),
			getX() + 12, getY() + 104, 0xFFA0A0A0);

		// State of the fluid IO gate, next to its button.
		if (gui.getMachine().getRedstoneElements().contains(RedstoneConfiguration.Element.FLUID_IO)) {
			RedstoneConfiguration configuration = gui.getMachine().getRedstoneConfiguration();
			drawText(drawContext, gui, Text.translatable("reborncore.gui.fluidconfig.fluid_io"),
				getX() - 4, getY() + 151, 0xFFFFFFFF);
			drawText(drawContext, gui, Text.translatable("reborncore.gui.fluidconfig."
					+ configuration.getState(RedstoneConfiguration.Element.FLUID_IO).name().toLowerCase(java.util.Locale.ROOT)),
				getX() + 44, getY() + 151, 0xFFA0A0A0);
		}

		// Highlight the current tank so the selector reads as a radio group rather
		// than as unrelated actions.
		int tankCount = Math.max(1, gui.getMachine().getTankCount());
		for (int i = 0; i < tankCount; i++) {
			if (i != selectedTank) {
				continue;
			}
			int sx = getX() - 6 + gui.getGuiLeft();
			int sy = getY() + 104 + i * 15 + gui.getGuiTop();
			drawContext.fill(sx - 1, sy - 1, sx + 14, sy + 14, 0x80FFFFFF);
		}
	}
}
