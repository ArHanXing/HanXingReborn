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

import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Internal machine inventory operations; no arrays proportional to aggregate item counts. */
public final class SpaceTaskInventory {
	public static final int MAX_DESCRIPTORS = 64;
	public static final int MAX_SLOTS = 64;

	private SpaceTaskInventory() {
	}

	/** Returns actual consumed stacks, preserving extra components, or null without mutation. */
	@Nullable
	public static List<ItemStack> consume(Inventory inventory, int[] slots, List<ItemStack> requirements) {
		validateSlots(inventory, slots);
		List<ItemStack> wanted = copyStacks(requirements);
		List<ItemStack> available = new ArrayList<>();
		for (int slot : slots) {
			available.add(inventory.getStack(slot).copy());
		}
		int itemStart = 1;
		int requirementStart = itemStart + slots.length;
		int sink = requirementStart + wanted.size();
		long[][] residual = new long[sink + 1][sink + 1];
		long total = 0;
		for (int i = 0; i < available.size(); i++) {
			ItemStack candidate = available.get(i);
			residual[0][itemStart + i] = candidate.isEmpty() ? 0 : candidate.getCount();
			for (int j = 0; j < wanted.size(); j++) {
				if (matches(wanted.get(j), candidate)) {
					residual[itemStart + i][requirementStart + j] = candidate.getCount();
				}
			}
		}
		for (int j = 0; j < wanted.size(); j++) {
			residual[requirementStart + j][sink] = wanted.get(j).getCount();
			total += wanted.get(j).getCount();
		}
		long flow = 0;
		int[] previous = new int[sink + 1];
		while (flow < total) {
			Arrays.fill(previous, -1);
			previous[0] = 0;
			ArrayDeque<Integer> queue = new ArrayDeque<>();
			queue.add(0);
			while (!queue.isEmpty() && previous[sink] == -1) {
				int from = queue.removeFirst();
				for (int to = 1; to <= sink; to++) {
					if (previous[to] == -1 && residual[from][to] > 0) {
						previous[to] = from;
						queue.addLast(to);
					}
				}
			}
			if (previous[sink] == -1) {
				return null;
			}
			long moved = total - flow;
			for (int node = sink; node != 0; node = previous[node]) {
				moved = Math.min(moved, residual[previous[node]][node]);
			}
			for (int node = sink; node != 0; node = previous[node]) {
				int from = previous[node];
				residual[from][node] -= moved;
				residual[node][from] += moved;
			}
			flow += moved;
		}
		// Commit the prepared plan once every requirement has an allocation.
		List<ItemStack> consumed = new ArrayList<>();
		for (int i = 0; i < slots.length; i++) {
			int count = Math.toIntExact(residual[itemStart + i][0]);
			if (count != 0) {
				ItemStack actual = available.get(i);
				consumed.add(actual.copyWithCount(count));
				int left = actual.getCount() - count;
				inventory.setStack(slots[i], left == 0 ? ItemStack.EMPTY : actual.copyWithCount(left));
			}
		}
		if (!consumed.isEmpty()) {
			inventory.markDirty();
		}
		return List.copyOf(consumed);
	}

	/** Requirement patches constrain only their explicitly configured component keys. */
	public static boolean matches(ItemStack requirement, ItemStack candidate) {
		if (requirement.isEmpty() || candidate.isEmpty() || !ItemStack.areItemsEqual(requirement, candidate)) {
			return false;
		}
		for (var entry : requirement.getComponentChanges().entrySet()) {
			Object actual = candidate.getComponents().get(entry.getKey());
			if (entry.getValue().isPresent() ? !Objects.equals(entry.getValue().get(), actual) : actual != null) {
				return false;
			}
		}
		return true;
	}

	public static boolean canFit(Inventory inventory, int[] slots, List<ItemStack> stacks) {
		return simulate(inventory, slots, stacks, false, false).isEmpty();
	}

	public static boolean fitsEmptyOutputs(Inventory inventory, int[] slots, List<ItemStack> stacks) {
		return simulate(inventory, slots, stacks, true, false).isEmpty();
	}

	/** Inserts as much as fits in deterministic slot order; returns every undelivered item. */
	public static List<ItemStack> insert(Inventory inventory, int[] slots, List<ItemStack> stacks) {
		return simulate(inventory, slots, stacks, false, true);
	}

	private static List<ItemStack> simulate(Inventory inventory, int[] slots, List<ItemStack> stacks, boolean empty, boolean commit) {
		validateSlots(inventory, slots);
		List<ItemStack> incoming = copyStacks(stacks);
		ItemStack[] contents = new ItemStack[slots.length];
		for (int i = 0; i < slots.length; i++) {
			contents[i] = empty ? ItemStack.EMPTY : inventory.getStack(slots[i]).copy();
		}
		List<ItemStack> remaining = new ArrayList<>();
		for (ItemStack stack : incoming) {
			int left = stack.getCount();
			// Merge existing exact-component stacks before occupying new slots.
			for (int pass = 0; pass < 2 && left > 0; pass++) {
				for (int i = 0; i < contents.length && left > 0; i++) {
					ItemStack current = contents[i];
					if (pass == 0 ? current.isEmpty() || !ItemStack.areItemsAndComponentsEqual(current, stack) : !current.isEmpty()) {
						continue;
					}
					int maximum = Math.max(0, Math.min(stack.getMaxCount(), inventory.getMaxCount(stack)));
					int present = current.isEmpty() ? 0 : current.getCount();
					int moved = (int) Math.min((long) left, Math.max(0L, (long) maximum - present));
					if (moved > 0) {
						contents[i] = stack.copyWithCount(present + moved);
						left -= moved;
					}
				}
			}
			if (left > 0) {
				remaining.add(stack.copyWithCount(left));
			}
		}
		if (commit) {
			boolean changed = false;
			for (int i = 0; i < contents.length; i++) {
				if (!ItemStack.areEqual(contents[i], inventory.getStack(slots[i]))) {
					inventory.setStack(slots[i], contents[i]);
					changed = true;
				}
			}
			if (changed) {
				inventory.markDirty();
			}
		}
		return List.copyOf(remaining);
	}

	static List<ItemStack> copyStacks(List<ItemStack> stacks) {
		Objects.requireNonNull(stacks, "stacks");
		if (stacks.size() > MAX_DESCRIPTORS) {
			throw new IllegalArgumentException("At most " + MAX_DESCRIPTORS + " item descriptors are supported");
		}
		List<ItemStack> result = new ArrayList<>(stacks.size());
		for (ItemStack stack : stacks) {
			if (stack == null || stack.isEmpty() || stack.getCount() <= 0) {
				throw new IllegalArgumentException("Expected a nonempty positive-count item descriptor");
			}
			result.add(stack.copy());
		}
		return List.copyOf(result);
	}

	private static void validateSlots(Inventory inventory, int[] slots) {
		Objects.requireNonNull(inventory, "inventory");
		Objects.requireNonNull(slots, "slots");
		if (slots.length > MAX_SLOTS) {
			throw new IllegalArgumentException("Too many machine inventory slots");
		}
		HashSet<Integer> seen = new HashSet<>();
		for (int slot : slots) {
			if (slot < 0 || slot >= inventory.size() || !seen.add(slot)) {
				throw new IllegalArgumentException("Invalid or duplicate machine slot " + slot);
			}
		}
	}
}
