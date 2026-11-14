/*
 * This file is part of TechReborn, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2020 TechReborn
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

package techreborn.compat.jade;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import techreborn.space.SpaceMachineState;
import techreborn.space.task.SpaceTask;
import techreborn.space.task.SpaceTaskContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/** Fresh viewer-specific wire data only; never retains task or player snapshots. */
final class SpaceTaskJade {
	static final String DATA_KEY = "space_task";
	private static final int MAX_DESCRIPTORS = 64;
	private static final Set<String> RUNNING_STATES = Set.of("running", "paused_power", "paused_redstone",
			"paused_structure", "paused_host", "paused_user");

	private SpaceTaskJade() { }

	/** Scope is resolved by the server for the requesting viewer on every call. */
	static NbtCompound snapshot(SpaceMachineState machine, SpaceTaskContainer container, UUID viewerScope,
			RegistryWrapper.WrapperLookup lookup, Function<Identifier, String> filteredName, long consumedPower) {
		NbtCompound data = new NbtCompound();
		SpaceTask task = container.task();
		if (machine.ownerLocked() || machine.ownerScope() == null || !machine.ownerScope().equals(viewerScope)
				|| task != null && !machine.ownerScope().equals(task.owner())) {
			return data;
		}
		String state = container.invalid() ? "invalid_task" : task == null ? "idle"
				: switch (task.phase()) {
					case WAITING_OUTPUT -> "waiting_output";
					case REFUNDING -> "refunding";
					case RUNNING -> task.userPaused() ? "paused_user"
							: RUNNING_STATES.contains(container.status()) ? container.status() : "running";
				};
		data.putString("state", state);
		data.putBoolean("auto", container.autoEnabled());
		Identifier body = task == null ? machine.selectedBody() : task.body();
		if (body != null) { data.putString("targetName", filteredName.apply(body)); }
		if (task == null) { return data; }
		// No task ID, owner UUID, body ID, private persistence or catalogue products.
		data.putString("kind", task.kind().name().toLowerCase(java.util.Locale.ROOT));
		data.putInt("tier", task.tier());
		data.putLong("elapsed", task.progress());
		data.putLong("duration", task.duration());
		data.putLong("requiredPower", task.power());
		data.putLong("actualPower", state.equals("running") ? Math.max(0, consumedPower) : 0);
		data.put("escrow", encodeStacks(task.escrow(), lookup));
		data.put("outputs", encodeStacks(task.outputs(), lookup));
		if (task.phase() != SpaceTask.Phase.RUNNING) {
			data.putString("pendingKind", task.phase() == SpaceTask.Phase.REFUNDING ? "refund" : "output");
			data.put("pending", encodeStacks(task.pending(), lookup));
		}
		return data;
	}

	static NbtList encodeStacks(List<ItemStack> stacks, RegistryWrapper.WrapperLookup lookup) {
		if (stacks.size() > MAX_DESCRIPTORS) { throw new IllegalArgumentException("Too many Jade space task descriptors"); }
		NbtList result = new NbtList();
		for (ItemStack stack : List.copyOf(stacks)) {
			if (stack.isEmpty() || stack.getCount() <= 0) { throw new IllegalArgumentException("Invalid Jade space task item"); }
			NbtCompound entry = new NbtCompound();
			entry.putInt("count", stack.getCount());
			entry.put("stack", stack.copyWithCount(1).encode(lookup));
			result.add(entry);
		}
		return result;
	}

	static List<ItemStack> decodeStacks(NbtList entries, RegistryWrapper.WrapperLookup lookup) {
		if (entries.size() > MAX_DESCRIPTORS || !entries.isEmpty() && entries.getHeldType() != NbtElement.COMPOUND_TYPE) {
			return List.of();
		}
		List<ItemStack> result = new ArrayList<>();
		for (int i = 0; i < entries.size(); i++) {
			NbtCompound entry = entries.getCompound(i);
			if (!entry.contains("count", NbtElement.INT_TYPE) || entry.getInt("count") <= 0
					|| !entry.contains("stack", NbtElement.COMPOUND_TYPE)) { return List.of(); }
			var decoded = ItemStack.CODEC.parse(RegistryOps.of(NbtOps.INSTANCE, lookup), entry.getCompound("stack"));
			if (decoded.error().isPresent() || decoded.result().isEmpty()) { return List.of(); }
			ItemStack stack = decoded.result().get();
			if (stack.isEmpty() || stack.getCount() != 1) { return List.of(); }
			result.add(stack.copyWithCount(entry.getInt("count")));
		}
		return List.copyOf(result);
	}

	/** Exact two decimal seconds, with no int truncation or floating-point rounding. */
	static String seconds(long ticks) {
		long positive = Math.max(0, ticks);
		return positive / 20 + "." + String.format(java.util.Locale.ROOT, "%02d", positive % 20 * 5);
	}
}
