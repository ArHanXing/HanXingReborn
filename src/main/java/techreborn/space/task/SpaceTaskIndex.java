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
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import techreborn.TechReborn;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Locator/lock index only. Escrow and output have one owner: the machine chunk. */
public final class SpaceTaskIndex extends PersistentState {
	private static final Type<SpaceTaskIndex> TYPE = new Type<>(SpaceTaskIndex::new, SpaceTaskIndex::read, null);
	private final Map<Location, Entry> entries = new HashMap<>();
	private long epoch;
	private long checkpoint;

	public record Location(Identifier dimension, BlockPos pos) {
		public Location { pos = pos.toImmutable(); }
	}
	public record Entry(Location location, UUID taskId, UUID owner, Identifier body, boolean exploration, boolean completed,
			boolean forceLoad, List<Long> chunks) {
		public Entry { chunks = List.copyOf(chunks); }
	}

	public static SpaceTaskIndex get(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "techreborn_space_tasks");
	}
	public long epoch() { return epoch; }
	public long checkpoint() { return checkpoint; }
	public void beginCheckpoint() { checkpoint++; markDirty(); }
	public void nextEpoch() { epoch++; markDirty(); }
	public List<Entry> entries() { return List.copyOf(entries.values()); }
	public Entry get(Location location) { return entries.get(location); }
	public void put(Entry entry) {
		if (!entry.equals(entries.put(entry.location(), entry))) { markDirty(); }
	}
	public void remove(Location location) { if (entries.remove(location) != null) { markDirty(); } }
	public boolean busy(UUID owner, Identifier body, Location except) {
		return entries.values().stream().anyMatch(entry -> !entry.location().equals(except) && entry.exploration()
				&& !entry.completed() && entry.owner().equals(owner) && entry.body().equals(body));
	}

	private static SpaceTaskIndex read(NbtCompound root, RegistryWrapper.WrapperLookup lookup) {
		SpaceTaskIndex data = new SpaceTaskIndex();
		data.epoch = Math.max(0, root.getLong("epoch"));
		data.checkpoint = Math.max(0, root.getLong("checkpoint"));
		NbtList list = root.getList("entries", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < list.size(); i++) {
			try {
				NbtCompound tag = list.getCompound(i);
				Location location = new Location(Identifier.of(tag.getString("dimension")), BlockPos.fromLong(tag.getLong("pos")));
				long[] chunks = tag.getLongArray("chunks");
				if (chunks.length > 256) { throw new IllegalArgumentException("too many task chunks"); }
				data.entries.put(location, new Entry(location, UUID.fromString(tag.getString("taskId")), UUID.fromString(tag.getString("owner")),
						Identifier.of(tag.getString("body")), tag.getBoolean("exploration"), tag.getBoolean("completed"), tag.getBoolean("forceLoad"),
						java.util.Arrays.stream(chunks).boxed().toList()));
			} catch (RuntimeException exception) {
				TechReborn.LOGGER.error("Skipping corrupt space task locator {}: {}", i, exception.getMessage());
			}
		}
		return data;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound root, RegistryWrapper.WrapperLookup lookup) {
		root.putLong("epoch", epoch);
		root.putLong("checkpoint", checkpoint);
		NbtList list = new NbtList();
		for (Entry entry : entries.values()) {
			NbtCompound tag = new NbtCompound();
			tag.putString("dimension", entry.location().dimension().toString());
			tag.putLong("pos", entry.location().pos().asLong());
			tag.putString("taskId", entry.taskId().toString());
			tag.putString("owner", entry.owner().toString());
			tag.putString("body", entry.body().toString());
			tag.putBoolean("exploration", entry.exploration());
			tag.putBoolean("completed", entry.completed());
			tag.putBoolean("forceLoad", entry.forceLoad());
			tag.putLongArray("chunks", entry.chunks());
			list.add(tag);
		}
		root.put("entries", list);
		return root;
	}
}
