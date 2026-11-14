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

/** Native machine screen displaying the current server-filtered catalogue. */
public abstract class GuiSpaceTerminal extends GuiMultiblockMachine {
	protected SpaceMapWidget map = new SpaceMapWidget(java.util.List.of());
	protected java.util.Map<String, SpaceClientView.Body> bodyViews = java.util.Map.of();
	protected boolean dataReady;
	protected boolean editable;
	protected boolean serverStructure;
	protected String serverReason = "";
	protected long catalogRevision = -1;
	private long snapshotSequence;
	private net.minecraft.nbt.NbtCompound terminalView = new net.minecraft.nbt.NbtCompound();
	private int refreshTicks;
	private boolean selectionDirty;
	private int selectionDelay;
	private String lastFocus = "";
	private boolean configurationPending;
	private long submittedConfigurationAck;
	private Selection submittedSelection;
	private long selectionGeneration;
	private long submittedSelectionGeneration;
	private int pendingTicks;
	private boolean actionPending;
	private long submittedActionAck;
	private long actionAck;
	private long configurationAck;
	protected net.minecraft.nbt.NbtCompound taskView = new net.minecraft.nbt.NbtCompound();
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
		requestSnapshot();
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
		if ((!dataReady ? !isStructureValid() : !serverStructure) && !isTabOpen()) {
			context.getMatrices().push();
			context.getMatrices().translate(x, y, 700);
			context.fill(8, 28, backgroundWidth - 8, 218, 0xA0707070);
			context.drawCenteredTextWithShadow(textRenderer, tr("structure_missing"), backgroundWidth / 2, 112, 0xFFFFFFFF);
			context.getMatrices().pop();
		}
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
		var detail = selectedView();
		previewStage = detail == null ? 0 : detail.stage();
		map.setSurveyed(previewStage > 0);
		long nowTick = client.world == null ? 0 : client.world.getTime();
		long eventTick = taskView.getLong("eventTick");
		double eventAge = eventTick == Long.MIN_VALUE || eventTick > nowTick || nowTick - eventTick > 80 ? 100 : (nowTick - eventTick) / 20D;
		map.setTaskVisuals(taskView.getString("taskBody"), taskView.getString("taskKind"), taskState(),
				(double) taskView.getLong("taskProgress") / Math.max(1, taskView.getLong("taskDuration")),
				taskView.getString("event"), taskView.getString("eventBody"), eventAge);
		map.render(context, mouseX, mouseY);
		drawHologramButton(context, mouseX, mouseY, Layer.BACKGROUND);
	}

	public long lastSnapshotSequence() {
		return snapshotSequence;
	}

	public void acceptSnapshot(long sequence, net.minecraft.nbt.NbtCompound snapshot) {
		if (sequence <= snapshotSequence) {
			return;
		}
		String focused = map.selected() == null ? "" : map.selected().id();
		var incoming = SpaceClientView.bodies(snapshot);
		boolean firstSnapshot = !dataReady;
		boolean catalogChanged = !dataReady || catalogRevision != snapshot.getLong("catalogRevision");
		// Rebuild the view only when its contents change: preserve camera on keepalive.
		if (catalogChanged || !snapshot.getList("bodies", 10).equals(terminalView.getList("bodies", 10))) {
			map = new SpaceMapWidget(incoming.values().stream().map(SpaceClientView.Body::mapBody).toList());
			String selected = catalogChanged && !snapshot.getString("selectedBody").isEmpty()
					? snapshot.getString("selectedBody") : focused;
			if (incoming.containsKey(selected)) {
				map.select(incoming.get(selected).mapBody());
			}
		}
		bodyViews = incoming;
		terminalView = snapshot;
		snapshotSequence = sequence;
		catalogRevision = snapshot.getLong("catalogRevision");
		editable = snapshot.getBoolean("editable");
		serverStructure = snapshot.getBoolean("structure");
		serverReason = snapshot.getString("reason");
		taskView = snapshot.getCompound("task");
		configurationAck = snapshot.getLong("configurationAck");
		actionAck = snapshot.getLong("actionAck");
		if (actionPending && (actionAck > submittedActionAck || catalogChanged || !editable)) {
			actionPending = false;
		}
		boolean applyConfirmedSelection = !selectionDirty && !configurationPending;
		if (configurationPending && configurationAck > submittedConfigurationAck) {
			configurationPending = false;
			// A failed request is still acknowledged. Revert only the submitted
			// draft, never a newer user edit that happened while it was in flight.
			if (submittedSelection != null && selectionGeneration == submittedSelectionGeneration) {
				selectionDirty = false;
				applyConfirmedSelection = true;
				if (!serverReason.isEmpty() && incoming.containsKey(snapshot.getString("selectedBody"))) {
					map.select(incoming.get(snapshot.getString("selectedBody")).mapBody());
					lastFocus = map.selected().id();
				}
			}
		}
		if (catalogChanged || !editable) {
			selectionDirty = false;
			configurationPending = false;
			applyConfirmedSelection = true;
		}
		if (!editable && isTabOpen()) {
			closeSelectedTab();
		}
		dataReady = true;
		if (applyConfirmedSelection) {
			deviceTier = snapshot.contains("selectedTier") ? snapshot.getInt("selectedTier") : 1;
			onSelectionSnapshot(snapshot);
		}

		if (firstSnapshot) {
			lastFocus = map.selected() == null ? "" : map.selected().id();
			if (snapshot.getString("selectedBody").isEmpty() && editable && serverStructure
					&& terminalMachine instanceof techreborn.blockentity.machine.multiblock.SpaceElevatorBlockEntity
					&& selectedView() != null && !selectedView().tiers().isEmpty()) {
				deviceTier = selectedView().tiers().getFirst();
				selectionChanged();
			}
		}
	}

	protected void onSelectionSnapshot(net.minecraft.nbt.NbtCompound snapshot) {
	}

	protected SpaceClientView.Body selectedView() {
		return map.selected() == null ? null : bodyViews.get(map.selected().id());
	}

	protected java.util.List<String> selectedResourceIds() {
		return java.util.List.of();
	}

	protected void selectionChanged() {
		selectionDirty = true;
		selectionGeneration++;
		selectionDelay = 3;
	}

	private void requestSnapshot() {
		if (client != null && client.player != null && net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(techreborn.space.SpaceTerminalRequest.ID)) {
			net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new techreborn.space.SpaceTerminalRequest(
					handler.syncId, handler.getPos(), catalogRevision, false, "", 1, java.util.List.of()));
		}
	}

	@Override
	protected void handledScreenTick() {
		super.handledScreenTick();
		String focus = map.selected() == null ? "" : map.selected().id();
		if (!lastFocus.equals(focus)) {
			lastFocus = focus;
			var detail = selectedView();
			if (detail != null && !detail.tiers().isEmpty() && !detail.tiers().contains(deviceTier)) {
				deviceTier = detail.tiers().getFirst();
			}
			if (terminalMachine instanceof techreborn.blockentity.machine.multiblock.SpaceElevatorBlockEntity
					&& detail != null && !detail.tiers().isEmpty() && editable && serverStructure) {
				selectionChanged();
			}
		}
		if ((configurationPending || actionPending) && ++pendingTicks > 100) {
			// The UI is never permanently stuck if a window was replaced or a
			// peer did not answer. The periodic authoritative view repairs it.
			configurationPending = false;
			actionPending = false;
			selectionDirty = false;
			serverReason = "sync_timeout";
			requestSnapshot();
		}
		if (++refreshTicks % 20 == 0) {
			requestSnapshot();
		}
		if (selectionDirty && !configurationPending && !actionPending && --selectionDelay <= 0
				&& map.selected() != null && editable && serverStructure) {
			submittedSelection = currentSelection();
			submittedSelectionGeneration = selectionGeneration;
			submittedConfigurationAck = configurationAck;
			configurationPending = true;
			pendingTicks = 0;
			net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new techreborn.space.SpaceTerminalRequest(
					handler.syncId, handler.getPos(), catalogRevision, true, submittedSelection.body(),
					submittedSelection.tier(), submittedSelection.resources()));
		}

	}

	private Selection currentSelection() {
		return new Selection(map.selected() == null ? "" : map.selected().id(), deviceTier,
				java.util.List.copyOf(selectedResourceIds()));
	}

	protected boolean selectionAcknowledged() {
		if (!dataReady || selectionDirty || configurationPending || actionPending) {
			return false;
		}
		var resources = terminalView.getList("selectedResources", 8);
		java.util.List<String> confirmed = new java.util.ArrayList<>();
		for (int i = 0; i < resources.size(); i++) {
			confirmed.add(resources.getString(i));
		}
		Selection selection = currentSelection();
		return selection.body().equals(terminalView.getString("selectedBody"))
				&& selection.tier() == terminalView.getInt("selectedTier")
				&& new java.util.HashSet<>(selection.resources()).equals(new java.util.HashSet<>(confirmed));
	}

	protected void taskAction(techreborn.space.SpaceTaskAction.Action action) {
		if (!editable || actionPending || !dataReady) {
			return;
		}
		boolean starting = action == techreborn.space.SpaceTaskAction.Action.START
				|| action == techreborn.space.SpaceTaskAction.Action.TOGGLE_AUTO && !autoEnabled();
		if (starting && (!serverStructure || !selectionAcknowledged())) {
			serverReason = "selection_pending";
			return;
		}
		Selection selection = currentSelection();
		actionPending = true;
		submittedActionAck = actionAck;
		pendingTicks = 0;
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new techreborn.space.SpaceTaskAction(
				handler.syncId, handler.getPos(), catalogRevision, action, selection.body(), selection.tier()));
	}

	protected boolean controlsPending() {
		return actionPending || configurationPending || selectionDirty;
	}

	protected boolean taskActionPending() {
		return actionPending;
	}

	protected boolean autoEnabled() {
		return taskView.getBoolean("autoEnabled");
	}

	protected String taskState() {
		String state = taskView.getString("taskState");
		return state.isEmpty() ? "idle" : state;
	}

	protected boolean taskActive() {
		return !taskState().equals("idle");
	}

	protected boolean canPauseTask() {
		return editable && !actionPending && taskActive() && !java.util.Set.of("waiting_output", "refunding", "invalid_task").contains(taskState());
	}

	protected String taskReason() {
		return serverReason.isEmpty() ? taskView.getString("reason") : serverReason;
	}

	protected int taskProgressScaled(int width) {
		long duration = Math.max(1, taskView.getLong("taskDuration"));
		return (int) (width * Math.min(1D, Math.max(0D, (double) taskView.getLong("taskProgress") / duration)));
	}

	protected Text taskStatusTooltip() {
		String body = taskView.getString("taskBody");
		Text name = bodyViews.containsKey(body) ? bodyViews.get(body).mapBody().name() : Text.literal(body);
		return tr("task_details", name, tr("task_state." + taskState()), taskView.getLong("taskProgress"),
				taskView.getLong("taskDuration"), taskView.getLong("taskPower"), stageName());
	}

	private record Selection(String body, int tier, java.util.List<String> resources) {
	}

	@Override
	public boolean isConfigEnabled() {
		// GuiBase constructs its tabs before the first snapshot arrives.
		return super.isConfigEnabled() && (!dataReady || editable);
	}

	protected int mapHeight() {
		return MAP_H;
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		super.drawForeground(context, mouseX, mouseY);
		label(context, tr(dataReady ? "server_banner" : "sync_pending"), 132, 19, 360, 0xFF9C6A24);
		button(context, 132, 28, 22, "<", true, mouseX, mouseY);
		button(context, 370, 28, 22, ">", true, mouseX, mouseY);
		label(context, Text.translatable("gui.techreborn.space.system." + map.system()), 160, 32, 204, INK);
		label(context, tr("inventory"), SpaceTerminalLayout.PLAYER_X, 225, 162, INK);
		button(context, 10, 265, 22, "<", true, mouseX, mouseY);
		button(context, 104, 265, 22, ">", true, mouseX, mouseY);
		label(context, tr("browse_bodies"), 38, 269, 62, INK);
		label(context, tr("controls"), 10, 287, 116, MUTED);
		button(context, 398, 223, 94, taskActive() ? tr(taskState().equals("paused_user") ? "resume_task" : "pause_task").getString()
				: tr("server_stage", stageName()).getString(), canPauseTask(), mouseX, mouseY);
		if (!isTabOpen() && taskActive() && inside(mouseX - x, mouseY - y, 398, 223, 94, 16)) {
			queueTooltip(taskStatusTooltip());
		}
		button(context, 398, 243, 94, tr("selected_tier", deviceTier).getString(),
				editable && serverStructure && selectedView() != null && !selectedView().tiers().isEmpty(), mouseX, mouseY);
		if (!isTabOpen() && inside(mouseX - x, mouseY - y, 398, 243, 94, 16) && selectedView() != null) {
			var schemes = selectedView().data().getList("variants", 10);
			for (int i = 0; i < schemes.size(); i++) {
				var scheme = schemes.getCompound(i);
				if (scheme.getInt("tier") == deviceTier) {
					queueTooltip(tr("scheme_summary", deviceTier, scheme.getLong("power"),
							String.format(java.util.Locale.ROOT, "%.2f", scheme.getLong("time") / 20D)));
				}
			}
		}
		SpaceBody body = map.selected();
		if (dataReady && body == null) {
			label(context, tr("empty_catalog"), 142, 102, 240, MUTED);
		}
		if (body != null) {
			label(context, body.name(), 14, 34, 106, INK);
			label(context, Text.translatable("gui.techreborn.space.type." + body.type()), 14, 48, 106, MUTED);
			label(context, tr("body_tier", body.tier()), 14, 60, 106, ACCENT);
			label(context, selectedView() != null && selectedView().data().getBoolean("physicalVisible") ? tr("radius", body.radiusKm()) : tr("physical_hidden"), 14, 72, 106, MUTED);
			label(context, selectedView() != null && selectedView().data().getBoolean("physicalVisible") ? tr("temperature", body.temperatureK()) : tr("survey_required"), 14, 82, 106, MUTED);
		}
		long energy = terminalMachine.getEnergy();
		long capacity = Math.max(1, terminalMachine.getMaxStoredPower());
		context.fill(398, 296, 492, 303, 0xFF354651);
		context.fill(399, 297, 399 + (int) (92 * Math.min(1D, (double) energy / capacity)), 302, ACCENT);
		label(context, Text.literal(energy + " EU"), 398, 306, 94, INK);
		drawSchemeInputs(context, mouseX, mouseY);
		drawMissingStructureOverlay(context, mouseX, mouseY, Layer.FOREGROUND);
	}

	/** Data-pack supplies, not a guessed spacecraft family. Count stays aggregate. */
	protected void drawSchemeInputs(DrawContext context, int mouseX, int mouseY) {
		if (selectedView() == null || client == null || client.world == null) { return; }
		var schemes = selectedView().data().getList("variants", 10);
		for (int s = 0; s < schemes.size(); s++) {
			var scheme = schemes.getCompound(s);
			if (scheme.getInt("tier") != deviceTier) { continue; }
			var inputs = scheme.getList("inputs", 10);
			if (inputs.isEmpty()) { label(context, tr("no_supplies"), 399, 268, 92, MUTED); }
			for (int i = 0; i < Math.min(9, inputs.size()); i++) {
				var entry = inputs.getCompound(i);
				ItemStack stack = ItemStack.fromNbt(client.world.getRegistryManager(), entry.getCompound("stack")).orElse(ItemStack.EMPTY);
				if (stack.isEmpty() || entry.getInt("count") <= 0) { continue; }
				stack = stack.copyWithCount(entry.getInt("count"));
				int sx = 399 + i % 5 * 18;
				int sy = 260 + i / 5 * 17;
				context.fill(sx - 1, sy - 1, sx + 17, sy + 16, 0xFFCEDBDF);
				context.getMatrices().push();
				context.getMatrices().translate(sx, sy, 0);
				context.getMatrices().scale(.9F, .9F, 1);
				context.drawItem(stack, 0, 0);
				context.drawItemInSlot(textRenderer, stack, 0, 0, Integer.toString(stack.getCount()));
				context.getMatrices().pop();
				if (!isTabOpen() && inside(mouseX - x, mouseY - y, sx - 1, sy - 1, 18, 17)) {
					queueTooltip(stack);
				}
			}
			return;
		}
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
		var systems = bodyViews.values().stream().map(view -> view.mapBody().system()).distinct().toList();
		if (systems.isEmpty()) {
			return;
		}
		int index = systems.indexOf(map.system());
		map.setSystem(systems.get(Math.floorMod(index + delta, systems.size())));
	}

	private void changeBody(int delta) {
		var bodies = bodyViews.values().stream().map(SpaceClientView.Body::mapBody).filter(body -> body.system().equals(map.system())).toList();
		if (!bodies.isEmpty()) {
			map.select(bodies.get(Math.floorMod(bodies.indexOf(map.selected()) + delta, bodies.size())));
		}
	}

	/** Browsing is local; validated configuration is submitted after a short debounce. */
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
			if (canPauseTask()) {
				taskAction(techreborn.space.SpaceTaskAction.Action.TOGGLE_PAUSE);
			}
			return true;
		}
		if (inside(lx, ly, 398, 243, 94, 16)) {
			if (editable && serverStructure && selectedView() != null) {
				var tiers = selectedView().tiers();
				if (!tiers.isEmpty()) {
					deviceTier = tiers.get(Math.floorMod(tiers.indexOf(deviceTier) + 1, tiers.size()));
					selectionChanged();
				}
			}
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
		if (dataReady && !editable && (isTabOpen() || focusedSlot != null)) {
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
