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

package techreborn.space.task;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import techreborn.TechReborn;

/** Machine-local state saved with inventory; invalid task data locks the machine. */
public final class SpaceTaskContainer {
	public static final String NBT_KEY = "spaceExecution";
	private final Runnable dirty;
	private SpaceTask task;
	private net.minecraft.nbt.NbtElement invalid;
	private final java.util.Map<net.minecraft.util.Identifier, Integer> completed = new java.util.HashMap<>();
	private long completionEpoch;
	private String fault = "";
	private boolean autoEnabled;
	private long epoch;
	private long revision;
	private String status = "idle";
	private String reason = "";
	private long lastConsumedPower;
	private int lastPaidTick = Integer.MIN_VALUE;
	public void consumed(long power, int serverTick) { lastConsumedPower = power; lastPaidTick = serverTick; }
	public long consumed(int serverTick) {
		return serverTick >= lastPaidTick && (long) serverTick - lastPaidTick <= 1 ? lastConsumedPower : 0;
	}

	private final java.util.Map<String, Long> visualCooldowns = new java.util.HashMap<>();
	private long visualSequence;
	private long visualTick = Long.MIN_VALUE;
	private String visualEvent = "";
	private String visualBody = "";

	public boolean visualEvent(String event, String body, long tick, int cooldown) {
		long previous = visualCooldowns.getOrDefault(event, Long.MIN_VALUE);
		if (previous != Long.MIN_VALUE && tick >= previous && tick - previous < cooldown) { return false; }
		visualCooldowns.put(event, tick);
		visualTick = tick;
		visualSequence++;
		visualEvent = event;
		visualBody = body;
		changed();
		return true;
	}
	public void writeVisual(net.minecraft.nbt.NbtCompound view) {
		view.putLong("eventSequence", visualSequence);
		view.putLong("eventTick", visualTick);
		view.putString("event", visualEvent);
		view.putString("eventBody", visualBody);
	}

	public SpaceTaskContainer(Runnable dirty) {
		this.dirty = dirty;
	}

	public SpaceTask task() { return task; }
	public boolean invalid() { return invalid != null || !fault.isEmpty(); }
	public void fault(String message) {
		if (!fault.equals(message)) { fault = message; changed(); }
		autoEnabled(false); status("invalid_task", "invalid_task");
	}
	public java.util.Map<net.minecraft.util.Identifier, Integer> completed() { return java.util.Map.copyOf(completed); }
	public long completionEpoch() { return completionEpoch; }
	public void acknowledgeCompletion(net.minecraft.util.Identifier body, int stage, long epoch) {
		completed.merge(body, stage, Math::max); completionEpoch = epoch; changed();
	}
	public void clearCompletions() { if (!completed.isEmpty()) { completed.clear(); changed(); } }
	public boolean autoEnabled() { return autoEnabled; }
	public long epoch() { return epoch; }
	public long revision() { return revision; }
	public String status() { return status; }
	public String reason() { return reason; }

	public void task(SpaceTask task) {
		if (invalid != null) { throw new IllegalStateException("Corrupt task requires administrator recovery"); }
		this.task = task;
		changed();
	}

	public void autoEnabled(boolean enabled) {
		if (autoEnabled != enabled) { autoEnabled = enabled; changed(); }
	}

	public void epoch(long epoch) {
		if (this.epoch != epoch) { this.epoch = epoch; changed(); }
	}

	public void status(String status, String reason) {
		if (!this.status.equals(status) || !this.reason.equals(reason)) {
			this.status = status;
			this.reason = reason;
			changed();
		}
	}

	public void changed() {
		revision++;
		dirty.run();
	}

	public void read(NbtCompound root, RegistryWrapper.WrapperLookup lookup) {
		task = null;
		invalid = null;
		autoEnabled = false;
		epoch = 0;
		fault = "";
		completed.clear();
		completionEpoch = 0;
		status = "idle"; reason = "";
		if (!root.contains(NBT_KEY)) { return; }
		if (!(root.get(NBT_KEY) instanceof NbtCompound tag)) {
			invalid = root.get(NBT_KEY).copy();
			status = "invalid_task"; return;
		}
		fault = tag.getString("fault");
		completionEpoch = tag.getLong("completionEpoch");
		NbtCompound stages = tag.getCompound("completed");
		for (String id : stages.getKeys()) {
			var body = net.minecraft.util.Identifier.tryParse(id);
			int stage = stages.getInt(id);
			if (body != null && stage >= 1 && stage <= 3) { completed.put(body, stage); }
		}
		autoEnabled = tag.getBoolean("auto");
		epoch = tag.getLong("epoch");
		if (tag.contains("task")) {
			try {
				task = SpaceTask.read(tag.getCompound("task"), lookup);
			} catch (RuntimeException exception) {
				invalid = tag.copy();
				autoEnabled = false;
				status = "invalid_task";
				TechReborn.LOGGER.error("Invalid space task retained for administrator recovery: {}", exception.getMessage());
			}
		}
	}

	public void write(NbtCompound root, RegistryWrapper.WrapperLookup lookup) {
		if (invalid != null) { root.put(NBT_KEY, invalid.copy()); return; }
		NbtCompound tag = new NbtCompound();
		tag.putBoolean("auto", autoEnabled);
		tag.putLong("epoch", epoch);
		tag.putString("fault", fault);
		tag.putLong("completionEpoch", completionEpoch);
		NbtCompound stages = new NbtCompound();
		completed.forEach((body, stage) -> stages.putInt(body.toString(), stage));
		tag.put("completed", stages);
		if (task != null) { tag.put("task", task.write(lookup)); }
		root.put(NBT_KEY, tag);
	}
}
