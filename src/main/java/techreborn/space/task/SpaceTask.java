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

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * One authoritative task owned and saved by its machine, alongside its inventory.
 * The caller marks that machine dirty after every task mutation. No global task
 * copy, wall-clock catch-up, chunk ticket or transient pause status lives here.
 */
public final class SpaceTask {
	public enum Kind {
		SURVEY, LAND, OUTPOST, MINING
	}

	public enum Phase {
		RUNNING, WAITING_OUTPUT, REFUNDING
	}

	private final UUID id;
	private final UUID owner;
	private final Identifier body;
	private final Kind kind;
	private final int tier;
	private final long power;
	private final long duration;
	private long progress;
	private final List<ItemStack> escrow;
	private final List<ItemStack> outputs;
	private Phase phase;
	private boolean userPaused;
	private List<ItemStack> pending = List.of();

	public SpaceTask(UUID owner, Identifier body, Kind kind, int tier, long power, long duration,
			List<ItemStack> escrow, List<ItemStack> outputs) {
		this(UUID.randomUUID(), owner, body, kind, tier, power, duration, escrow, outputs);
	}

	private SpaceTask(UUID id, UUID owner, Identifier body, Kind kind, int tier, long power, long duration,
			List<ItemStack> escrow, List<ItemStack> outputs) {
		this.id = Objects.requireNonNull(id, "task id");
		this.owner = Objects.requireNonNull(owner, "owner");
		this.body = Objects.requireNonNull(body, "body");
		this.kind = Objects.requireNonNull(kind, "kind");
		if (body.getPath().isBlank() || tier < 1 || tier > 3 || power <= 0 || duration <= 0) {
			throw new IllegalArgumentException("Invalid task body/tier/power/duration");
		}
		this.tier = tier;
		this.power = power;
		this.duration = duration;
		this.escrow = SpaceTaskInventory.copyStacks(escrow);
		this.outputs = SpaceTaskInventory.copyStacks(outputs);
		phase = Phase.RUNNING;
	}

	public UUID id() {
		return id;
	}

	public UUID owner() {
		return owner;
	}

	public Identifier body() {
		return body;
	}

	public Kind kind() {
		return kind;
	}

	public int tier() {
		return tier;
	}

	public long power() {
		return power;
	}

	public long duration() {
		return duration;
	}

	public long progress() {
		return progress;
	}

	public Phase phase() {
		return phase;
	}

	public boolean userPaused() {
		return userPaused;
	}

	public List<ItemStack> escrow() {
		return SpaceTaskInventory.copyStacks(escrow);
	}

	public List<ItemStack> outputs() {
		return SpaceTaskInventory.copyStacks(outputs);
	}

	public List<ItemStack> pending() {
		return SpaceTaskInventory.copyStacks(pending);
	}

	public void setUserPaused(boolean paused) {
		userPaused = paused;
	}

	/** Call only after successfully charging one whole tick of power. */
	public boolean tickProgress() {
		if (phase != Phase.RUNNING || userPaused) {
			return false;
		}
		progress++;
		if (progress == duration) {
			complete();
			return true;
		}
		return false;
	}

	/** Idempotent: already delivered outputs are never recreated on a second call. */
	public void complete() {
		if (phase == Phase.RUNNING) {
			progress = duration;
			pending = SpaceTaskInventory.copyStacks(outputs);
			phase = Phase.WAITING_OUTPUT;
		}
	}

	/** Only unfinished work refunds; completed delivery and repeated calls are unchanged. */
	public void refund() {
		if (phase == Phase.RUNNING) {
			pending = SpaceTaskInventory.copyStacks(escrow);
			phase = Phase.REFUNDING;
		}
	}

	/** Store the result of partial insertion in the same machine/save as its inventory. */
	public void replacePending(List<ItemStack> remaining) {
		if (phase == Phase.RUNNING) {
			throw new IllegalStateException("A running task has no pending delivery");
		}
		List<ItemStack> replacement = SpaceTaskInventory.copyStacks(remaining);
		if (!isSubset(replacement, pending)) {
			throw new IllegalArgumentException("Pending delivery may only shrink without changing item components");
		}
		pending = replacement;
	}

	private static boolean isSubset(List<ItemStack> subset, List<ItemStack> full) {
		long[] available = new long[full.size()];
		for (int i = 0; i < full.size(); i++) {
			available[i] = full.get(i).getCount();
		}
		for (ItemStack stack : subset) {
			long needed = stack.getCount();
			for (int i = 0; i < full.size() && needed > 0; i++) {
				if (ItemStack.areItemsAndComponentsEqual(stack, full.get(i))) {
					long used = Math.min(available[i], needed);
					available[i] -= used;
					needed -= used;
				}
			}
			if (needed != 0) {
				return false;
			}
		}
		return true;
	}

	public NbtCompound write(RegistryWrapper.WrapperLookup lookup) {
		NbtCompound tag = new NbtCompound();
		tag.putInt("version", 1);
		tag.putString("id", id.toString());
		tag.putString("owner", owner.toString());
		tag.putString("body", body.toString());
		tag.putString("kind", kind.name());
		tag.putInt("tier", tier);
		tag.putLong("power", power);
		tag.putLong("duration", duration);
		tag.putLong("progress", progress);
		tag.putString("phase", phase.name());
		tag.putBoolean("userPaused", userPaused);
		tag.put("escrow", writeStacks(escrow, lookup));
		tag.put("outputs", writeStacks(outputs, lookup));
		tag.put("pending", writeStacks(pending, lookup));
		return tag;
	}

	/** Strict fail-closed read. The caller retains the invalid raw blob and blocks the machine. */
	public static SpaceTask read(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
		try {
			require(tag, "version", NbtElement.INT_TYPE);
			if (tag.getInt("version") != 1) {
				throw new IllegalArgumentException("unsupported version");
			}
			for (String field : List.of("id", "owner", "body", "kind", "phase")) {
				require(tag, field, NbtElement.STRING_TYPE);
			}
			require(tag, "tier", NbtElement.INT_TYPE);
			for (String field : List.of("power", "duration", "progress")) {
				require(tag, field, NbtElement.LONG_TYPE);
			}
			require(tag, "userPaused", NbtElement.BYTE_TYPE);
			if (tag.getByte("userPaused") != 0 && tag.getByte("userPaused") != 1) {
				throw new IllegalArgumentException("userPaused must be boolean");
			}
			String bodyId = tag.getString("body");
			Identifier body = bodyId.isBlank() ? null : Identifier.tryParse(bodyId);
			if (body == null) {
				throw new IllegalArgumentException("invalid body ID");
			}
			SpaceTask task = new SpaceTask(uuid(tag.getString("id"), "id"), uuid(tag.getString("owner"), "owner"),
					body, Kind.valueOf(tag.getString("kind")), tag.getInt("tier"), tag.getLong("power"),
					tag.getLong("duration"), readStacks(tag, "escrow", lookup), readStacks(tag, "outputs", lookup));
			task.progress = tag.getLong("progress");
			task.phase = Phase.valueOf(tag.getString("phase"));
			task.userPaused = tag.getBoolean("userPaused");
			task.pending = readStacks(tag, "pending", lookup);
			if (task.progress < 0 || task.progress > task.duration
					|| task.phase == Phase.WAITING_OUTPUT && task.progress != task.duration
					|| task.phase != Phase.WAITING_OUTPUT && task.progress == task.duration
					|| task.phase == Phase.RUNNING && !task.pending.isEmpty()
					|| task.phase == Phase.WAITING_OUTPUT && !isSubset(task.pending, task.outputs)
					|| task.phase == Phase.REFUNDING && !isSubset(task.pending, task.escrow)) {
				throw new IllegalArgumentException("inconsistent progress/phase/pending items");
			}
			return task;
		} catch (RuntimeException exception) {
			throw new IllegalArgumentException("Invalid persisted space task: " + exception.getMessage(), exception);
		}
	}

	private static UUID uuid(String value, String field) {
		UUID parsed = UUID.fromString(value);
		if (!parsed.toString().equals(value)) {
			throw new IllegalArgumentException("invalid canonical UUID in " + field);
		}
		return parsed;
	}

	private static NbtList writeStacks(List<ItemStack> stacks, RegistryWrapper.WrapperLookup lookup) {
		NbtList list = new NbtList();
		for (ItemStack stack : stacks) {
			NbtCompound entry = new NbtCompound();
			entry.putInt("count", stack.getCount());
			entry.put("stack", stack.copyWithCount(1).encode(lookup));
			list.add(entry);
		}
		return list;
	}

	private static List<ItemStack> readStacks(NbtCompound tag, String field, RegistryWrapper.WrapperLookup lookup) {
		require(tag, field, NbtElement.LIST_TYPE);
		NbtList list = (NbtList) tag.get(field);
		if (list.size() > SpaceTaskInventory.MAX_DESCRIPTORS) {
			throw new IllegalArgumentException(field + " has too many item descriptors");
		}
		List<ItemStack> result = new ArrayList<>();
		for (int i = 0; i < list.size(); i++) {
			if (!(list.get(i) instanceof NbtCompound entry)) {
				throw new IllegalArgumentException(field + "[" + i + "] must be a compound");
			}
			require(entry, "count", NbtElement.INT_TYPE);
			require(entry, "stack", NbtElement.COMPOUND_TYPE);
			if (entry.getInt("count") <= 0) {
				throw new IllegalArgumentException(field + "[" + i + "].count must be positive");
			}
			var decoded = ItemStack.CODEC.parse(RegistryOps.of(NbtOps.INSTANCE, lookup), entry.getCompound("stack"));
			if (decoded.error().isPresent()) {
				throw new IllegalArgumentException(field + "[" + i + "].stack: " + decoded.error().get().message());
			}
			ItemStack unit = decoded.result().orElseThrow();
			if (unit.isEmpty() || unit.getCount() != 1) {
				throw new IllegalArgumentException(field + "[" + i + "].stack must be one nonempty item");
			}
			result.add(unit.copyWithCount(entry.getInt("count")));
		}
		return List.copyOf(result);
	}

	private static void require(NbtCompound tag, String field, int type) {
		if (!tag.contains(field, type)) {
			throw new IllegalArgumentException(field + " has missing/wrong NBT type");
		}
	}
}
