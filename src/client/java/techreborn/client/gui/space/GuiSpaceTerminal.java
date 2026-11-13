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

package techreborn.client.gui.space;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.item.ItemStack;
import reborncore.common.screen.BuiltScreenHandler;
import techreborn.blockentity.machine.multiblock.JsonMultiblockMachineBlockEntity;
import techreborn.client.gui.GuiMultiblockMachine;
import techreborn.space.SpaceTerminalLayout;

/** Native machine screen with an embedded, client-only mission preview. */
public abstract class GuiSpaceTerminal extends GuiMultiblockMachine {
	protected final SpaceMapWidget map = new SpaceMapWidget(SpacePreviewCatalog.bodies());
	protected int previewStage;
	protected int deviceTier = 1;
	private float terminalScale = 1;
	protected static final int INK = 0xFF243746;
	protected static final int MUTED = 0xFF637682;
	protected static final int ACCENT = 0xFF167F92;
	protected static final int MAP_X = 132;
	protected static final int MAP_Y = 43;
	protected static final int MAP_W = 260;
	protected static final int MAP_H = 174;

	private final JsonMultiblockMachineBlockEntity terminalMachine;
	private Text clippedTooltip;
	private ItemStack itemTooltip = ItemStack.EMPTY;
	private int pointerX;
	private int pointerY;

	protected GuiSpaceTerminal(PlayerEntity player, JsonMultiblockMachineBlockEntity machine, BuiltScreenHandler handler) {
		super(player, machine, handler);
		terminalMachine = machine;
		backgroundWidth = SpaceTerminalLayout.WIDTH;
		backgroundHeight = SpaceTerminalLayout.HEIGHT;
	}

	@Override
	public void init() {
		// Keep all native slot/config coordinates in one logical coordinate system.
		// Render AND pointer input use the same transform on smaller GUI resolutions.
		int physicalWidth = client.getWindow().getScaledWidth();
		int physicalHeight = client.getWindow().getScaledHeight();
		terminalScale = Math.min(1F, Math.min((physicalWidth - 56F) / backgroundWidth,
				(physicalHeight - 12F) / backgroundHeight));
		terminalScale = Math.max(0.1F, terminalScale);
		width = Math.round(physicalWidth / terminalScale);
		height = Math.round(physicalHeight / terminalScale);
		super.init();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		pointerX = Math.round(mouseX / terminalScale);
		pointerY = Math.round(mouseY / terminalScale);
		clippedTooltip = null;
		itemTooltip = ItemStack.EMPTY;
		context.getMatrices().push();
		context.getMatrices().scale(terminalScale, terminalScale, 1);
		super.render(context, pointerX, pointerY, delta);
		context.getMatrices().pop();
		// Vanilla tooltip bounds use physical window dimensions, so render after
		// leaving the logical terminal transform (also avoids foreground origin).
		if (!itemTooltip.isEmpty()) {
			context.drawItemTooltip(textRenderer, itemTooltip, mouseX, mouseY);
		} else if (clippedTooltip != null) {
			context.drawTooltip(textRenderer, clippedTooltip, mouseX, mouseY);
		}
	}

	protected void queueTooltip(Text text) {
		clippedTooltip = text;
	}

	protected void queueTooltip(ItemStack stack) {
		itemTooltip = stack;
	}

	@Override
	protected void drawMouseoverTooltip(DrawContext context, int mouseX, int mouseY) {
		if (handler.getCursorStack().isEmpty() && focusedSlot != null && focusedSlot.hasStack()) {
			queueTooltip(focusedSlot.getStack());
		}
		int offset = upgrades ? 86 : 6;
		for (var tab : getTabs()) {
			if (!tab.enabled()) {
				continue;
			}
			if (inside(mouseX - x, mouseY - y, -26, offset, 24, 23)) {
				queueTooltip(Text.translatable(tab.name()));
			}
			offset += 24;
		}
	}

	@Override
	protected boolean drawMultiblockMissingBar() {
		return false;
	}

	@Override
	protected int hologramMissingButtonX() {
		return hologramButtonX();
	}

	@Override
	protected int hologramMissingButtonY() {
		return hologramButtonY();
	}

	@Override
	protected int getPlayerInventoryOffset() {
		return SpaceTerminalLayout.PLAYER_Y - 94;
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		super.drawBackground(context, delta, mouseX, mouseY);
		panel(context, 8, 28, 118, 190);
		panel(context, 398, 28, 94, 190);
		map.setBounds(x + MAP_X, y + MAP_Y, MAP_W, mapHeight());
		map.setClipScale(terminalScale);
		map.setSurveyed(previewStage > 0);
		map.render(context, mouseX, mouseY);
		drawHologramButton(context, mouseX, mouseY, Layer.BACKGROUND);
	}

	protected int mapHeight() {
		return MAP_H;
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		super.drawForeground(context, mouseX, mouseY);
		label(context, tr("preview_banner"), 132, 19, 360, 0xFF9C6A24);
		button(context, 132, 28, 22, "<", true, mouseX, mouseY);
		button(context, 370, 28, 22, ">", true, mouseX, mouseY);
		label(context, Text.translatable("gui.techreborn.space.system." + map.system()), 160, 32, 204, INK);
		label(context, tr("inventory"), SpaceTerminalLayout.PLAYER_X, 225, 162, INK);
		button(context, 10, 265, 22, "<", true, mouseX, mouseY);
		button(context, 104, 265, 22, ">", true, mouseX, mouseY);
		label(context, tr("browse_bodies"), 38, 269, 62, INK);
		label(context, tr("controls"), 10, 287, 116, MUTED);
		button(context, 398, 223, 94, tr("preview_stage", stageName()).getString(), true, mouseX, mouseY);
		button(context, 398, 243, 94, tr("device_tier", deviceTier).getString(), true, mouseX, mouseY);
		SpaceBody body = map.selected();
		if (body != null) {
			label(context, body.name(), 14, 34, 106, INK);
			label(context, Text.translatable("gui.techreborn.space.type." + body.type()), 14, 48, 106, MUTED);
			label(context, tr("body_tier", body.tier()), 14, 60, 106, ACCENT);
			label(context, previewStage > 0 ? tr("radius", body.radiusKm()) : tr("physical_hidden"), 14, 72, 106, MUTED);
			label(context, previewStage > 0 ? tr("temperature", body.temperatureK()) : tr("survey_required"), 14, 82, 106, MUTED);
		}
		long energy = terminalMachine.getEnergy();
		long capacity = Math.max(1, terminalMachine.getMaxStoredPower());
		context.fill(398, 296, 492, 303, 0xFF354651);
		context.fill(399, 297, 399 + (int) (92 * Math.min(1D, (double) energy / capacity)), 302, ACCENT);
		label(context, Text.literal(energy + " EU"), 398, 306, 94, INK);
		if (!isStructureValid()) {
			label(context, tr("structure_missing"), 398, 282, 94, 0xFFB84A39);
		}
		drawMissingStructureOverlay(context, mouseX, mouseY, Layer.FOREGROUND);
	}

	protected Text stageName() {
		return tr("stage." + new String[]{"unknown", "surveyed", "landed", "outpost"}[previewStage]);
	}

	protected static Text tr(String key, Object... args) {
		return Text.translatable("gui.techreborn.space." + key, args);
	}

	protected void label(DrawContext context, Text text, int localX, int localY, int maxWidth, int color) {
		String value = text.getString();
		if (textRenderer.getWidth(value) > maxWidth) {
			if (inside(pointerX - x, pointerY - y, localX, localY, maxWidth, 9)) {
				clippedTooltip = text;
			}
			value = textRenderer.trimToWidth(value, Math.max(0, maxWidth - textRenderer.getWidth("…"))) + "…";
		}
		context.drawText(textRenderer, value, localX, localY, color, false);
	}

	private void panel(DrawContext context, int localX, int localY, int w, int h) {
		context.fill(x + localX, y + localY, x + localX + w, y + localY + h, 0xFFE2E8E8);
		context.drawBorder(x + localX, y + localY, w, h, 0xFFA4B5BA);
	}

	protected void button(DrawContext context, int bx, int by, int w, String text, boolean enabled, int mx, int my) {
		boolean hover = inside(mx - x, my - y, bx, by, w, 16);
		context.fill(bx, by, bx + w, by + 16, enabled ? (hover ? 0xFF207E91 : 0xFF355D6C) : 0xFF78868B);
		label(context, Text.literal(text), bx + 4, by + 4, w - 8, 0xFFFFFFFF);
	}

	protected static boolean inside(double mx, double my, int bx, int by, int w, int h) {
		return mx >= bx && mx < bx + w && my >= by && my < by + h;
	}

	private void changeSystem(int delta) {
		var systems = SpacePreviewCatalog.systems();
		int index = systems.indexOf(map.system());
		map.setSystem(systems.get(Math.floorMod(index + delta, systems.size())));
	}

	private void changeBody(int delta) {
		var bodies = SpacePreviewCatalog.bodies().stream().filter(body -> body.system().equals(map.system())).toList();
		if (!bodies.isEmpty()) {
			map.select(bodies.get(Math.floorMod(bodies.indexOf(map.selected()) + delta, bodies.size())));
		}
	}

	/** Local preview actions only; never sends a mission or modifies machine state. */
	protected boolean terminalClick(double mx, double my, int button) {
		if (button != 0) {
			return false;
		}
		double lx = mx - x;
		double ly = my - y;
		if (inside(lx, ly, 132, 28, 22, 16)) {
			changeSystem(-1);
			return true;
		}
		if (inside(lx, ly, 370, 28, 22, 16)) {
			changeSystem(1);
			return true;
		}
		if (inside(lx, ly, 398, 223, 94, 16)) {
			previewStage = (previewStage + 1) % 4;
			return true;
		}
		if (inside(lx, ly, 398, 243, 94, 16)) {
			deviceTier = deviceTier % 3 + 1;
			return true;
		}
		if (inside(lx, ly, 10, 265, 22, 16)) {
			changeBody(-1);
			return true;
		}
		if (inside(lx, ly, 104, 265, 22, 16)) {
			changeBody(1);
			return true;
		}
		return map.mouseClicked(mx, my, button);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		double mx = mouseX / terminalScale;
		double my = mouseY / terminalScale;
		if (!isTabOpen() && terminalClick(mx, my, button)) {
			return true;
		}
		return super.mouseClicked(mx, my, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
		double mx = mouseX / terminalScale;
		double my = mouseY / terminalScale;
		if (!isTabOpen() && map.mouseDragged(mx, my, button, dx / terminalScale, dy / terminalScale)) {
			return true;
		}
		return super.mouseDragged(mx, my, button, dx / terminalScale, dy / terminalScale);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		double mx = mouseX / terminalScale;
		double my = mouseY / terminalScale;
		// Always clear map drag state, including when a native config tab is open.
		boolean mapRelease = map.mouseReleased(mx, my, button);
		return super.mouseReleased(mx, my, button) || mapRelease;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		double mx = mouseX / terminalScale;
		double my = mouseY / terminalScale;
		if (!isTabOpen() && terminalScroll(mx, my, vertical)) {
			return true;
		}
		return super.mouseScrolled(mx, my, horizontal, vertical);
	}

	protected boolean terminalScroll(double mx, double my, double amount) {
		return map.mouseScrolled(mx, my, amount);
	}
}
