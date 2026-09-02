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

package techreborn.world;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-wide persistence for the Dyson Swarm system.
 * <p>
 * The solar sails fed into a Dyson Swarm Host permanently increase the sail
 * count of the bound player (even after the host is broken), and every Dyson
 * Swarm Receiver bound to that player produces energy proportional to the
 * count. Data is stored per player UUID in the overworld's level storage
 * (one host and one receiver may be bound to a player at a time).
 */
public class DysonSwarmData extends PersistentState {

	private static final String DATA_NAME = "techreborn_dyson_swarm";
	private static final String KEY_SAILS = "sails";

	private static final Type<DysonSwarmData> TYPE = new Type<>(DysonSwarmData::new,
			DysonSwarmData::fromNbt, net.minecraft.datafixer.DataFixTypes.SAVED_DATA_MAP_DATA);

	/** Sail count per player. */
	private final Map<UUID, Long> sails = new HashMap<>();
	/** Bound host location per player (dimension id + block position). */
	private final Map<UUID, Binding> hosts = new HashMap<>();
	/** Bound receiver location per player (dimension id + block position). */
	private final Map<UUID, Binding> receivers = new HashMap<>();

	private DysonSwarmData() {
	}

	/**
	 * @param world {@link World} any server world
	 * @return the shared Dyson Swarm data, or {@code null} when called on the
	 *         client or before the server is fully started
	 */
	public static DysonSwarmData get(World world) {
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

	public long getSailCount(UUID player) {
		if (player == null) {
			return 0;
		}
		return sails.getOrDefault(player, 0L);
	}

	/**
	 * Adds sails that were fed into a bound host.
	 *
	 * @param player {@link UUID} the bound player
	 * @param amount {@code int} number of sails to add
	 */
	public void addSails(UUID player, int amount) {
		sails.merge(player, (long) amount, Long::sum);
		markDirty();
	}

	public Binding getHostBinding(UUID player) {
		return player == null ? null : hosts.get(player);
	}

	public Binding getReceiverBinding(UUID player) {
		return player == null ? null : receivers.get(player);
	}

	public boolean isHostBoundAt(UUID player, String dimension, BlockPos pos) {
		return bindingAt(getHostBinding(player), dimension, pos);
	}

	public boolean isReceiverBoundAt(UUID player, String dimension, BlockPos pos) {
		return bindingAt(getReceiverBinding(player), dimension, pos);
	}

	private static boolean bindingAt(Binding binding, String dimension, BlockPos pos) {
		return binding != null && binding.matches(dimension, pos);
	}

	/**
	 * Binds the host at the given location to the player, replacing any
	 * previous (possibly stale) host binding.
	 */
	public void bindHost(UUID player, String dimension, BlockPos pos) {
		hosts.put(player, new Binding(dimension, pos.asLong()));
		markDirty();
	}

	/**
	 * Binds the receiver at the given location to the player, replacing any
	 * previous (possibly stale) receiver binding.
	 */
	public void bindReceiver(UUID player, String dimension, BlockPos pos) {
		receivers.put(player, new Binding(dimension, pos.asLong()));
		markDirty();
	}

	/**
	 * Removes the host binding of the player when it still points at the given
	 * location (no-op otherwise, so a freshly re-bound machine is not cleared
	 * by a stale removal).
	 */
	public void unbindHost(UUID player, String dimension, BlockPos pos) {
		if (bindingAt(hosts.get(player), dimension, pos)) {
			hosts.remove(player);
			markDirty();
		}
	}

	/**
	 * Removes the receiver binding of the player when it still points at the
	 * given location (no-op otherwise, see {@link #unbindHost}).
	 */
	public void unbindReceiver(UUID player, String dimension, BlockPos pos) {
		if (bindingAt(receivers.get(player), dimension, pos)) {
			receivers.remove(player);
			markDirty();
		}
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		NbtList list = new NbtList();
		sails.forEach((uuid, count) -> {
			NbtCompound entry = new NbtCompound();
			entry.putUuid("uuid", uuid);
			entry.putLong(KEY_SAILS, count);
			Binding host = hosts.get(uuid);
			Binding receiver = receivers.get(uuid);
			if (host != null) {
				entry.putString("hostDim", host.dimension);
				entry.putLong("hostPos", host.pos);
			}
			if (receiver != null) {
				entry.putString("receiverDim", receiver.dimension);
				entry.putLong("receiverPos", receiver.pos);
			}
			list.add(entry);
		});
		nbt.put("entries", list);
		return nbt;
	}

	private static DysonSwarmData fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		DysonSwarmData data = new DysonSwarmData();
		NbtList list = nbt.getList("entries", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < list.size(); i++) {
			NbtCompound entry = list.getCompound(i);
			UUID uuid = entry.getUuid("uuid");
			data.sails.put(uuid, entry.getLong(KEY_SAILS));
			if (entry.contains("hostDim")) {
				data.hosts.put(uuid, new Binding(entry.getString("hostDim"), entry.getLong("hostPos")));
			}
			if (entry.contains("receiverDim")) {
				data.receivers.put(uuid, new Binding(entry.getString("receiverDim"), entry.getLong("receiverPos")));
			}
		}
		return data;
	}

	/**
	 * Location of a bound machine: dimension registry name plus packed block
	 * position (see {@link BlockPos#asLong()}).
	 */
	public record Binding(String dimension, long pos) {

		public boolean matches(String otherDimension, BlockPos otherPos) {
			return dimension.equals(otherDimension) && pos == otherPos.asLong();
		}
	}
}
