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
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import techreborn.blockentity.machine.multiblock.SpaceElevatorMinerBlockEntity;
import techreborn.client.gui.space.GuiSpaceTerminal;
import techreborn.client.gui.space.SpaceBody;
import techreborn.init.TRContent;
import techreborn.items.SpacecraftItem;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Star-map and resource selection preview; legacy production is unchanged. */
public class GuiSpaceElevatorMiner extends GuiSpaceTerminal {
	private final SpaceElevatorMinerBlockEntity blockEntity;
	private final Map<String, Set<Integer>> selections = new HashMap<>();
	private String target;
	private String resourceBody;
	private int resourceRow;

	public GuiSpaceElevatorMiner(int syncID, PlayerEntity player, SpaceElevatorMinerBlockEntity blockEntity) {
		super(player, blockEntity, blockEntity.createScreenHandler(syncID, player));
		this.blockEntity = blockEntity;
	}

	@Override
	protected int mapHeight() {
		return 130;
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		super.drawBackground(context, delta, mouseX, mouseY);
		for (int i = 0; i < 4; i++) {
			drawSlot(context, 18 + i % 2 * 18, 102 + i / 2 * 18, Layer.BACKGROUND);
		}
		for (int i = 0; i < 6; i++) {
			drawSlot(context, 18 + i % 3 * 18, 166 + i / 3 * 18, Layer.BACKGROUND);
		}
		drawSlot(context, 90, 102, Layer.BACKGROUND);
	}

	private boolean hasPreviewOutpost() {
		return previewStage >= 3 && map.selected() != null && !map.selected().type().equals("black_hole");
	}

	private Set<Integer> selectedResources(SpaceBody body) {
		return selections.computeIfAbsent(body.id(), ignored -> new LinkedHashSet<>());
	}

	private List<ItemStack> resources() {
		SpaceBody body = map.selected();
		if (body == null || previewStage == 0) {
			return List.of();
		}
		if (!body.id().equals(resourceBody)) {
			resourceBody = body.id();
			resourceRow = 0;
		}
		return body.resources();
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		super.drawForeground(context, mouseX, mouseY);
		label(context, tr("inputs"), 14, 91, 106, INK);
		label(context, tr("outputs"), 14, 155, 106, INK);
		SpaceBody body = map.selected();
		label(context, tr("resource_pool"), 132, 178, 150, INK);
		if (body != null) {
			label(context, tr("selected_count", selectedResources(body).size()), 292, 178, 100, ACCENT);
		}
		List<ItemStack> resources = resources();
		context.fill(132, 190, 392, 217, 0xFF182D3A);
		if (resources.isEmpty()) {
			label(context, tr(previewStage == 0 ? "resources_hidden" : "no_resources"), 140, 199, 244, 0xFFB5C9D0);
		}
		ItemStack hovered = ItemStack.EMPTY;
		for (int i = 0; i < 8 && i + resourceRow * 8 < resources.size(); i++) {
			int index = i + resourceRow * 8;
			int rx = 138 + i % 8 * 31;
			int ry = 194 + i / 8 * 26;
			boolean selected = body != null && selectedResources(body).contains(index);
			context.fill(rx - 1, ry - 1, rx + 25, ry + 23, selected ? 0xFF248B95 : 0xFF3E5360);
			ItemStack stack = resources.get(index);
			context.drawItem(stack, rx, ry);
			int count = hasPreviewOutpost() ? stack.getCount() : stack.getCount() / 5;
			label(context, Text.literal(Integer.toString(count)), rx + 16, ry + 13, 10, 0xFFFFFFFF);
			if (inside(mouseX - x, mouseY - y, rx - 1, ry - 1, 26, 24)) {
				hovered = stack;
			}
		}
		label(context, tr("mission_status"), 404, 34, 82, INK);
		String reason = body == null || body.type().equals("star") ? "display_only"
				: previewStage == 0 ? "survey_required"
				: deviceTier < body.tier() ? "tier_insufficient"
				: previewStage < 2 && !body.type().equals("black_hole") ? "landing_required"
				: selectedResources(body).isEmpty() ? "select_resources" : "backend_pending";
		label(context, tr(reason), 404, 49, 82, 0xFFAA5736);
		label(context, tr(hasPreviewOutpost() ? "outpost_mode" : "long_range_mode"), 404, 66, 82, ACCENT);
		label(context, tr("output_factor", hasPreviewOutpost() ? "1.0" : "0.2"), 404, 80, 82, INK);
		if (body != null && !body.type().equals("star")) {
			double factor = Math.pow(0.25, Math.max(0, deviceTier - body.tier())) * (hasPreviewOutpost() ? 1 : 5);
			label(context, tr("time_factor", String.format(java.util.Locale.ROOT, "%.4g", factor)), 404, 94, 82, INK);
		}
		if (target != null) {
			label(context, tr("preview_target"), 404, 119, 82, INK);
			label(context, Text.literal(target), 404, 132, 82, ACCENT);
		}
		button(context, 398, 161, 94, tr("focus_target").getString(), body != null && !body.resources().isEmpty(), mouseX, mouseY);
		button(context, 10, 223, 116, tr("action.mining").getString(), false, mouseX, mouseY);
		label(context, tr("legacy_processing"), 10, 243, 116, MUTED);
		int progress = blockEntity.getProgressScaled(100);
		context.fill(398, 190, 492, 195, 0xFF354651);
		context.fill(398, 190, 398 + Math.max(0, Math.min(94, progress * 94 / 100)), 195, ACCENT);
		context.drawItem(TRContent.Spacecraft.get(SpacecraftItem.Family.DRONE, deviceTier, true).getDefaultStack(), 398, 262);
		if (!isTabOpen()) {
			if (!hovered.isEmpty()) {
				queueTooltip(hovered);
			} else if (inside(mouseX - x, mouseY - y, 10, 223, 116, 16)) {
				queueTooltip(tr(reason));
			}
		}
	}

	@Override
	protected boolean terminalClick(double mx, double my, int button) {
		double lx = mx - x;
		double ly = my - y;
		SpaceBody body = map.selected();
		if (button == 0 && body != null && inside(lx, ly, 398, 161, 94, 16) && !body.resources().isEmpty()) {
			target = body.id();
			return true;
		}
		if (button == 0 && inside(lx, ly, 132, 190, 260, 27)) {
			List<ItemStack> resources = resources();
			for (int i = 0; i < 8 && i + resourceRow * 8 < resources.size(); i++) {
				if (body != null && inside(lx, ly, 137 + i % 8 * 31, 193 + i / 8 * 26, 26, 24)) {
					Set<Integer> chosen = selectedResources(body);
					int index = i + resourceRow * 8;
					if (!chosen.remove(index) && chosen.size() < 3) {
						chosen.add(index);
					}
					return true;
				}
			}
			return true;
		}
		return super.terminalClick(mx, my, button);
	}

	@Override
	protected boolean terminalScroll(double mx, double my, double amount) {
		if (inside(mx - x, my - y, 132, 190, 260, 27)) {
			int rows = (resources().size() + 7) / 8;
			resourceRow = Math.max(0, Math.min(Math.max(0, rows - 1), resourceRow + (amount < 0 ? 1 : -1)));
			return true;
		}
		return super.terminalScroll(mx, my, amount);
	}
}
