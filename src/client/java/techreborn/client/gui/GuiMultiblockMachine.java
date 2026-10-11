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

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;

import reborncore.client.gui.GuiBase;
import reborncore.client.gui.widget.GuiButtonExtended;
import reborncore.client.gui.widget.GuiButtonHologram;
import reborncore.common.blockentity.MachineBaseBlockEntity;
import reborncore.common.screen.BuiltScreenHandler;

/**
 * Base class of every multiblock machine GUI.
 * <p>
 * It owns what those GUIs used to copy into themselves: the hologram toggle, the
 * grey "structure missing" overlay, and the structure state that drives them.
 * <p>
 * The toggle is created exactly once (in {@link #init()}) and only moved when the
 * structure state flips. The previous per-GUI code called
 * {@code addHologramButton(...)} from {@code drawForeground}, which runs every
 * frame: each frame appended another clickable widget to the screen and none was
 * ever removed, so an open GUI grew its child list by roughly 60 entries per
 * second and every later render and input pass walked all of them. That leak is
 * why this class exists.
 * <p>
 * Subclasses call {@link #drawHologramButton} from {@code drawBackground} (the
 * toggle in the corner while the structure is formed) and
 * {@link #drawMissingStructureOverlay} from {@code drawForeground} (the grey
 * overlay plus the centred toggle while it is not), which keeps the original
 * draw order. Both refresh {@link #isStructureValid()} in the same frame.
 */
public abstract class GuiMultiblockMachine extends GuiBase<BuiltScreenHandler> {

	/** X of the toggle while the structure is formed. */
	protected int hologramButtonX() {
		return 6;
	}

	/** Y of the toggle while the structure is formed. */
	protected int hologramButtonY() {
		return 4;
	}

	/** X of the toggle while the structure is missing. */
	protected int hologramMissingButtonX() {
		return 76;
	}

	/** Y of the toggle while the structure is missing. */
	protected int hologramMissingButtonY() {
		return 56;
	}

	/**
	 * @return {@code boolean} whether the grey overlay is drawn while the
	 *         structure is missing; GUIs that show their own hint opt out
	 */
	protected boolean drawMultiblockMissingBar() {
		return true;
	}

	private GuiButtonHologram hologramButton;
	private boolean structureValid;
	/** Guards {@link #refreshStructureState()} so the structure is queried once per frame. */
	private boolean refreshedThisFrame;

	protected GuiMultiblockMachine(PlayerEntity player, BlockEntity machine, BuiltScreenHandler handler) {
		super(player, machine, handler);
	}

	/**
	 * @return {@link MachineBaseBlockEntity} the machine this GUI belongs to
	 */
	protected MachineBaseBlockEntity machine() {
		return (MachineBaseBlockEntity) be;
	}

	/**
	 * @return {@code boolean} the structure state as of this frame
	 */
	protected final boolean isStructureValid() {
		return structureValid;
	}

	@Override
	public void init() {
		super.init();
		structureValid = machine().isMultiblockValid();
		GuiButtonHologram button = addHologramButton(hologramButtonX(), hologramButtonY(), 212, Layer.FOREGROUND);
		button.clickHandler(this::toggleHologram);
		hologramButton = button;
		moveHologramButton();
	}

	/**
	 * Draws the hologram toggle in its formed-structure position. Call from
	 * {@code drawBackground}, before anything else is drawn over it.
	 *
	 * @param drawContext {@link DrawContext} the draw context
	 * @param mouseX      {@code int} mouse X, for the toggle tooltip
	 * @param mouseY      {@code int} mouse Y, for the toggle tooltip
	 * @param layer       {@link Layer} the layer the GUI is currently drawing in
	 */
	protected final void drawHologramButton(DrawContext drawContext, int mouseX, int mouseY, Layer layer) {
		refreshStructureState();
		if (structureValid) {
			builder.drawHologramButton(drawContext, this, hologramButtonX(), hologramButtonY(), mouseX, mouseY, layer);
		}
	}

	/**
	 * Draws the grey overlay and the centred toggle while the structure is
	 * missing, and nothing at all while it is formed. Call from
	 * {@code drawForeground} in place of the old per-GUI branch.
	 *
	 * @param drawContext {@link DrawContext} the draw context
	 * @param mouseX      {@code int} mouse X, for the toggle tooltip
	 * @param mouseY      {@code int} mouse Y, for the toggle tooltip
	 * @param layer       {@link Layer} the layer the GUI is currently drawing in
	 */
	protected final void drawMissingStructureOverlay(DrawContext drawContext, int mouseX, int mouseY, Layer layer) {
		refreshStructureState();
		if (structureValid) {
			return;
		}
		if (drawMultiblockMissingBar()) {
			builder.drawMultiblockMissingBar(drawContext, this, layer);
		}
		builder.drawHologramButton(drawContext, this, hologramMissingButtonX(), hologramMissingButtonY(), mouseX, mouseY, layer);
	}

	/**
	 * Toggles the multiblock hologram, like the other multiblock machines.
	 */
	protected void toggleHologram(GuiButtonExtended button, Double mouseX, Double mouseY) {
		machine().renderMultiblock ^= !hideGuiElements();
	}

	@Override
	public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
		// The structure is verified once per frame, in drawBackground
		refreshedThisFrame = false;
		super.render(drawContext, mouseX, mouseY, delta);
	}

	private void refreshStructureState() {
		if (refreshedThisFrame) {
			return;
		}
		refreshedThisFrame = true;
		boolean valid = machine().isMultiblockValid();
		if (valid != structureValid) {
			structureValid = valid;
			moveHologramButton();
		}
	}

	private void moveHologramButton() {
		// setX/setY take absolute screen coordinates, unlike addHologramButton()
		hologramButton.setX((structureValid ? hologramButtonX() : hologramMissingButtonX()) + getGuiLeft());
		hologramButton.setY((structureValid ? hologramButtonY() : hologramMissingButtonY()) + getGuiTop());
	}
}
