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

		// Layout, in panel-relative coordinates:
		//
		//   +------------------------------------------------+
		//   |  [ six-face grid ]        储罐选择器            |
		//   |  [               ]        [1] [2] [3]          |
		//   |                           当前: 1              |
		//   |  [ ] 抽取                                      |
		//   |  [ ] 泵出                                      |
		//   |  --------------------------------------------  |
		//   |  [X] 流体 I/O                          忽略     |
		//   +------------------------------------------------+
		//
		// Two columns keep the height near the size of the face grid instead of
		// stacking every control downwards, which used to push the panel well past
		// the GUI's own frame.
		elements.add(popupElement = new FluidConfigPopupElement(x + 4, y + 4, getHeight(), this));
		elements.add(new ButtonElement(x + 136, y - 25, GuiSprites.EXIT_BUTTON, 13, 13, gui::closeSelectedTab));

		// Tank selector: a horizontal row in the right column, under its heading.
		int tankCount = Math.max(1, gui.getMachine().getTankCount());
		for (int i = 0; i < tankCount; i++) {
			final int tankID = i;
			elements.add(new ButtonElement(TANK_X + i * TANK_STEP, TANK_Y, GuiSprites.EXIT_BUTTON, 13, 13,
				() -> selectTank(tankID, gui)));
		}

		elements.add(new CheckBoxElement(Text.translatable("reborncore.gui.fluidconfig.pullin"), x + 2, y + 68,
			checkBoxElement -> holder(gui).autoInput(),
			() -> popupElement.updateCheckBox("input", gui)));
		elements.add(new CheckBoxElement(Text.translatable("reborncore.gui.fluidconfig.pumpout"), x + 2, y + 83,
			checkBoxElement -> holder(gui).autoOutput(),
			() -> popupElement.updateCheckBox("output", gui)));

		// The redstone gate that decides whether fluid IO runs at all used to sit
		// on its own page, splitting one concern across two screens. It belongs to
		// this panel: the tank selector picks *which* tank, this decides whether
		// its IO is live at all.
		if (gui.getMachine().getRedstoneElements().contains(RedstoneConfiguration.Element.FLUID_IO)) {
			elements.add(new ButtonElement(x + 4, y + 102, GuiSprites.EXIT_BUTTON, 13, 13,
				() -> cycleFluidGate(gui)));
		}
	}

	/** Right column: where the tank selector buttons start, and their pitch. */
	private static final int TANK_X = 96;
	private static final int TANK_Y = 20;
	private static final int TANK_STEP = 16;

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
		// Two columns: the face grid plus switches on the left, the tank selector
		// on the right.
		return 156;
	}

	@Override
	public int getHeight() {
		// Roughly one face grid plus the switches and the gate row; the selector
		// sits beside the grid instead of below it.
		return 124;
	}

	@Override
	public void draw(DrawContext drawContext, GuiBase<?> gui, int mouseX, int mouseY) {
		if (isMouseWithinRect(gui, mouseX, mouseY)) {
			drawSprite(drawContext, gui, type.getButtonHoverOverlay(), getX(), getY(), type.getTextureWidth(), type.getTextureHeight());
		}
		super.draw(drawContext, gui, mouseX, mouseY);

		// Coordinates below are panel-relative: drawText and drawSprite add the GUI
		// origin themselves.
		int col2 = getX() + 92;

		// Right column: tank selector heading, buttons (drawn by the elements) and
		// the current selection under them.
		drawText(drawContext, gui, Text.translatable("reborncore.gui.fluidconfig.tank_selector"),
			col2, getY() + 8, 0xFFFFFFFF);
		int tankCount = Math.max(1, gui.getMachine().getTankCount());
		drawText(drawContext, gui, Text.translatable("reborncore.gui.fluidconfig.tank_current", selectedTank + 1),
			col2, getY() + 40, 0xFFA0A0A0);

		// Highlight the selected tank, matching the buttons' own coordinates.
		for (int i = 0; i < tankCount; i++) {
			if (i != selectedTank) {
				continue;
			}
			int sx = getX() + TANK_X + i * TANK_STEP + gui.getGuiLeft();
			int sy = getY() + TANK_Y + gui.getGuiTop();
			drawContext.fill(sx - 1, sy - 1, sx + 14, sy + 14, 0x80FFFFFF);
		}

		// Bottom row: fluid IO gate.
		if (gui.getMachine().getRedstoneElements().contains(RedstoneConfiguration.Element.FLUID_IO)) {
			RedstoneConfiguration configuration = gui.getMachine().getRedstoneConfiguration();
			drawText(drawContext, gui, Text.translatable("reborncore.gui.fluidconfig.fluid_io"),
				getX() + 22, getY() + 104, 0xFFFFFFFF);
			drawText(drawContext, gui, Text.translatable("reborncore.gui.fluidconfig."
					+ configuration.getState(RedstoneConfiguration.Element.FLUID_IO).name().toLowerCase(java.util.Locale.ROOT)),
				getX() + 92, getY() + 104, 0xFFA0A0A0);
		}
	}
}
