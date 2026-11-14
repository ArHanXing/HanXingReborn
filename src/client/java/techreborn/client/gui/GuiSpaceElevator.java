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
import reborncore.common.screen.BuiltScreenHandlerProvider;
import techreborn.blockentity.machine.multiblock.SpaceElevatorBlockEntity;
import techreborn.client.gui.space.GuiSpaceTerminal;
import techreborn.space.SpaceTerminalLayout;

/** Mission terminal with authoritative launch and task controls. */
public class GuiSpaceElevator<T extends SpaceElevatorBlockEntity & BuiltScreenHandlerProvider> extends GuiSpaceTerminal {
	private final T blockEntity;

	public GuiSpaceElevator(int syncID, PlayerEntity player, T blockEntity) {
		super(player, blockEntity, blockEntity.createScreenHandler(syncID, player));
		this.blockEntity = blockEntity;
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		super.drawBackground(context, delta, mouseX, mouseY);
		for (int i = 0; i < 9; i++) {
			drawSlot(context, SpaceTerminalLayout.INPUT_X + i % 3 * 18,
					SpaceTerminalLayout.INPUT_Y + i / 3 * 18, Layer.BACKGROUND);
			drawSlot(context, SpaceTerminalLayout.INPUT_X + i % 3 * 18,
					SpaceTerminalLayout.OUTPUT_Y + i / 3 * 18, Layer.BACKGROUND);
		}
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		super.drawForeground(context, mouseX, mouseY);
		label(context, tr("inputs"), 14, 91, 106, INK);
		label(context, tr("outputs"), 14, 155, 106, INK);
		String summary = blockEntity.getModuleSummary();
		String[] lines = summary.isBlank() ? new String[0] : summary.strip().split("\n");
		label(context, tr("modules", lines.length, SpaceElevatorBlockEntity.MAX_MODULES), 404, 34, 82, INK);
		for (int i = 0; i < lines.length && i < SpaceElevatorBlockEntity.MAX_MODULES; i++) {
			String[] parts = lines[i].split(" ");
			if (parts.length < 4) {
				continue;
			}
			int lineY = 49 + i * 16;
			label(context, tr(parts[0].equals("A") ? "assembler" : parts[0].equals("U") ? "module_unloaded" : "miner"), 404, lineY, 82, ACCENT);
			label(context, Text.literal(parts[1] + " " + parts[2] + " " + parts[3]), 404, lineY + 8, 82, MUTED);
		}
		String action = previewStage == 0 ? "survey" : previewStage == 1 ? "land" : "outpost";
		String reason = launchReason();
		boolean unavailable = map.selected() == null || selectedView() == null || selectedView().tiers().isEmpty();
		button(context, 10, 223, 116, tr(unavailable ? "no_stage" : "action." + action).getString(), reason.isEmpty(), mouseX, mouseY);
		label(context, tr(taskReason().isEmpty() ? reason.isEmpty() ? "ready" : reason : taskReason()), 10, 243, 116, MUTED);
		if (taskActive()) {
			context.fill(398, 181, 492, 187, 0xFF354651);
			context.fill(398, 181, 398 + taskProgressScaled(94), 187, ACCENT);
			label(context, tr("task_state." + taskState()), 398, 196, 94, ACCENT);
		}

		if (!isTabOpen() && inside(mouseX - x, mouseY - y, 10, 223, 116, 16)) {
			queueTooltip(tr(reason.isEmpty() ? "ready" : reason));
		}
	}
	private String launchReason() {
		if (!dataReady) return "sync_pending";
		if (!editable) return "no_permission";
		if (!serverStructure) return "structure_missing";
		if (taskActive()) return taskState().equals("refunding") ? "refunding" : "task_busy";
		if (selectedView() == null || selectedView().tiers().isEmpty()) return "display_only";
		if (!selectedView().tiers().contains(deviceTier)) return "scheme_unavailable";
		if (!selectionAcknowledged()) return "selection_pending";
		return "";
	}

	@Override
	protected boolean terminalClick(double mx, double my, int button) {
		if (button == 0 && inside(mx - x, my - y, 10, 223, 116, 16)) {
			if (launchReason().isEmpty()) {
				taskAction(techreborn.space.SpaceTaskAction.Action.START);
			}
			return true;
		}
		return super.terminalClick(mx, my, button);
	}

}
