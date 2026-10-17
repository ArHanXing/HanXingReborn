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

package techreborn.world;

import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-chunk fluid reserves mined by the Void Drill.
 * <p>
 * <b>Initial value.</b> A chunk's reserve is derived from the world seed and the
 * chunk coordinates, so it is stable across restarts without ever being written
 * to disk. Only the <i>remaining</i> amount of a chunk that has actually been
 * mined is stored here, which keeps the save file proportional to the number of
 * chunks players have drilled rather than to the world size.
 * <p>
 * Reserves are per dimension: the same chunk coordinates in the Nether and the
 * Overworld are independent.
 */
public class VoidDrillData extends PersistentState {

	private static final String DATA_NAME = "techreborn_void_drill";
	private static final Type<VoidDrillData> TYPE = new Type<>(VoidDrillData::new,
			VoidDrillData::fromNbt, DataFixTypes.SAVED_DATA_MAP_DATA);

	/** Smallest reserve a chunk can roll, 100 buckets. */
	public static final long MIN_RESERVE_MB = 100_000L;
	/** Largest reserve a chunk can roll, 10 000 buckets. */
	public static final long MAX_RESERVE_MB = 10_000_000L;

	/** Remaining fluid in millibuckets, keyed by dimension and chunk. */
	private final Map<String, Long> remaining = new HashMap<>();

	private VoidDrillData() {
	}

	/**
	 * @param world {@link World} any server world
	 * @return the shared reserve data, or {@code null} on the client or before
	 *         the server is fully started
	 */
	public static VoidDrillData get(World world) {
		if (world == null || world.isClient) {
			return null;
		}
		MinecraftServer server = world.getServer();
		if (server == null) {
			return null;
		}
		ServerWorld overworld = server.getOverworld();
		if (overworld == null) {
			return null;
		}
		PersistentStateManager manager = overworld.getPersistentStateManager();
		return manager.getOrCreate(TYPE, DATA_NAME);
	}

	/**
	 * @param world {@link ServerWorld} the dimension
	 * @param pos   {@link ChunkPos} the chunk
	 * @return {@code long} fluid left in that chunk, in millibuckets
	 */
	public static long remaining(ServerWorld world, ChunkPos pos) {
		VoidDrillData data = get(world);
		if (data == null) {
			return 0;
		}
		Long stored = data.remaining.get(key(world.getRegistryKey(), pos));
		if (stored != null) {
			return Math.max(0, stored);
		}
		return initialReserve(world.getSeed(), pos.x, pos.z);
	}

	/**
	 * Subtracts from a chunk's reserve, clamping at zero.
	 *
	 * @param world  {@link ServerWorld} the dimension
	 * @param pos    {@link ChunkPos} the chunk
	 * @param amount {@code long} millibuckets to remove
	 */
	public static void consume(ServerWorld world, ChunkPos pos, long amount) {
		VoidDrillData data = get(world);
		if (data == null || amount <= 0) {
			return;
		}
		long left = Math.max(0, remaining(world, pos) - amount);
		data.remaining.put(key(world.getRegistryKey(), pos), left);
		// Storing the value is what freezes the chunk's roll; do it even when it
		// reaches zero so an exhausted chunk does not roll again on reload.
		data.markDirty();
	}

	/**
	 * Deterministically turns (seed, chunk) into a reserve.
	 * <p>
	 * Uses the MurmurHash3 finaliser: cheap, well mixed, and identical on every
	 * launch, so no random value ever has to be persisted.
	 *
	 * @param seed   {@code long} the world seed
	 * @param chunkX {@code int} chunk X
	 * @param chunkZ {@code int} chunk Z
	 * @return {@code long} the chunk's full reserve, in millibuckets
	 */
	public static long initialReserve(long seed, int chunkX, int chunkZ) {
		long hash = seed;
		hash ^= (long) chunkX * 0x9E3779B97F4A7C15L;
		hash ^= (long) chunkZ * 0xC2B2AE3D27D4EB4FL;
		hash ^= hash >>> 33;
		hash *= 0xFF51AFD7ED558CCDL;
		hash ^= hash >>> 33;
		hash *= 0xC4CEB9FE1A85EC53L;
		hash ^= hash >>> 33;
		long span = MAX_RESERVE_MB - MIN_RESERVE_MB + 1;
		return MIN_RESERVE_MB + Math.floorMod(hash, span);
	}

	private static String key(RegistryKey<World> dimension, ChunkPos pos) {
		return dimension.getValue() + "#" + pos.x + "#" + pos.z;
	}

	private static VoidDrillData fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		VoidDrillData data = new VoidDrillData();
		NbtList chunks = nbt.getList("chunks", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < chunks.size(); i++) {
			NbtCompound entry = chunks.getCompound(i);
			data.remaining.put(entry.getString("key"), entry.getLong("remaining"));
		}
		return data;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		NbtList chunks = new NbtList();
		remaining.forEach((key, value) -> {
			NbtCompound entry = new NbtCompound();
			entry.putString("key", key);
			entry.putLong("remaining", value);
			chunks.add(entry);
		});
		nbt.put("chunks", chunks);
		return nbt;
	}
}
