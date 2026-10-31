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

import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import reborncore.client.gui.GuiBase;
import reborncore.client.gui.GuiSprites;
import reborncore.common.blockentity.FluidConfiguration;

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

		// The tank selector: one entry per configurable tank, above the face map.
		int tankCount = Math.max(1, gui.getMachine().getTankCount());
		for (int i = 0; i < tankCount; i++) {
			final int tankID = i;
			elements.add(new ButtonElement(x - 22 + i * 20, y - 18, GuiSprites.EXIT_BUTTON, 13, 13,
				() -> selectTank(tankID, gui)));
		}

		elements.add(new CheckBoxElement(Text.translatable("reborncore.gui.fluidconfig.pullin"), x - 26, y + 59,
			checkBoxElement -> holder(gui).autoInput(),
			() -> popupElement.updateCheckBox("input", gui)));
		elements.add(new CheckBoxElement(Text.translatable("reborncore.gui.fluidconfig.pumpout"), x - 26, y + 74,
			checkBoxElement -> holder(gui).autoOutput(),
			() -> popupElement.updateCheckBox("output", gui)));
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
		return 85;
	}

	@Override
	public int getHeight() {
		return 122;
	}

	@Override
	public void draw(DrawContext drawContext, GuiBase<?> gui, int mouseX, int mouseY) {
		if (isMouseWithinRect(gui, mouseX, mouseY)) {
			drawSprite(drawContext, gui, type.getButtonHoverOverlay(), getX(), getY(), type.getTextureWidth(), type.getTextureHeight());
		}
		super.draw(drawContext, gui, mouseX, mouseY);

		// Label + current selection, so the row of buttons is readable on its own.
		drawContext.drawText(gui.getTextRenderer(),
			Text.translatable("reborncore.gui.fluidconfig.tank_selector"),
			getX() - 22 + gui.getGuiLeft(), getY() - 32 + gui.getGuiTop(), 0xFFFFFFFF, false);
		drawContext.drawText(gui.getTextRenderer(),
			Text.translatable("reborncore.gui.fluidconfig.tank_current", selectedTank + 1),
			getX() - 22 + gui.getGuiLeft(), getY() - 4 + gui.getGuiTop(), 0xFFA0A0A0, false);

		// Highlight the current tank so the selector reads as a radio group rather
		// than as unrelated actions.
		int tankCount = Math.max(1, gui.getMachine().getTankCount());
		for (int i = 0; i < tankCount; i++) {
			if (i != selectedTank) {
				continue;
			}
			int sx = getX() - 22 + i * 20 + gui.getGuiLeft();
			int sy = getY() - 18 + gui.getGuiTop();
			drawContext.fill(sx - 1, sy - 1, sx + 14, sy + 14, 0x80FFFFFF);
		}
	}
}
