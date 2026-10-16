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

package techreborn.blockentity.cable;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Direction;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Weighted union-find over the energy cables.
 * <p>
 * Every cable is a node, and two cables are unioned when they are adjacent and
 * share a {@code transferRate}; {@link #find} therefore answers "which network
 * is this cable on" in near-constant time. Each set carries a
 * {@link CableNetwork} holding the aggregate weights (member count, buffer,
 * capacity and the last tick's throughput), which is what the Jade tooltip
 * reads.
 * <p>
 * <b>Splitting.</b> Union-find cannot delete, but cutting a cable in the middle
 * of a network has to split it in two. That is handled by invalidation rather
 * than by surgery: anything that can change the topology (a neighbouring block
 * changing, a member's chunk unloading, a cable being removed) marks the
 * network {@code dirty}, and the next {@link #resolve} re-discovers the
 * component with a BFS and rebuilds the sets from scratch. The BFS is the same
 * one the tick manager always used, so the membership it produces is exactly
 * the membership the energy transfer is written against.
 * <p>
 * Server side only: {@link CableBlockEntity#tick} returns early on the client,
 * and nothing else calls into here.
 */
public final class CableNetworkManager {

	/** Union-find parent links. A missing key means "not registered". */
	private static final Map<CableBlockEntity, CableBlockEntity> PARENT = new IdentityHashMap<>();
	/** The network of each union-find root. */
	private static final Map<CableBlockEntity, CableNetwork> NETWORKS = new IdentityHashMap<>();

	private CableNetworkManager() {
	}

	/**
	 * Drops every registration. Called when the server stops so a world change
	 * cannot leave cables from the previous world behind.
	 */
	public static void clear() {
		PARENT.clear();
		NETWORKS.clear();
	}

	/**
	 * @param cable {@link CableBlockEntity} the cable
	 * @return {@link CableNetwork} the network the cable is on, or {@code null}
	 *         when it is not registered
	 */
	public static CableNetwork networkOf(CableBlockEntity cable) {
		CableBlockEntity root = find(cable);
		return root == null ? null : NETWORKS.get(root);
	}

	/**
	 * Resolves the network of the cable, re-discovering the component when the
	 * cached membership is missing, was invalidated, or reaches a chunk that is
	 * no longer loaded.
	 * <p>
	 * The cheap "already processed this tick" test comes first on purpose.
	 * Without it every one of the N cables on a network would run the O(N)
	 * loaded-chunk scan below, turning a tick into O(N^2). With it exactly one
	 * cable per network pays for the scan, and the rest are an O(1) lookup -
	 * which is the same shape the old per-cable {@code lastTick} guard had.
	 *
	 * @param cable {@link CableBlockEntity} the cable
	 * @param tick  {@code long} the current server tick counter
	 * @return {@link CableNetwork} the (possibly rebuilt) network
	 */
	static CableNetwork resolve(CableBlockEntity cable, long tick) {
		CableNetwork network = networkOf(cable);
		if (network == null) {
			return rebuild(cable);
		}
		// Already handled this tick: the membership is good enough for it, and a
		// topology change noticed during the tick has to wait for the next one
		// anyway, because re-processing would run the transfers twice.
		if (network.lastTick() == tick) {
			return network;
		}
		if (!network.isDirty() && network.allMembersLoaded()) {
			return network;
		}
		return rebuild(cable);
	}

	/**
	 * Marks the cable's network as needing re-discovery. Cheap and safe to call
	 * repeatedly; every neighbour update funnels through here.
	 *
	 * @param cable {@link CableBlockEntity} the cable whose network changed
	 */
	static void invalidate(CableBlockEntity cable) {
		CableNetwork network = networkOf(cable);
		if (network != null) {
			network.markDirty();
		}
	}

	/**
	 * Removes a cable that left the world.
	 * <p>
	 * If the cable happened to be the union-find representative, the network is
	 * handed to a surviving member: keeping the map keyed by a block entity that
	 * no longer exists would both leak it and break every later {@code find}.
	 *
	 * @param cable {@link CableBlockEntity} the removed cable
	 */
	static void unregister(CableBlockEntity cable) {
		CableBlockEntity root = find(cable);
		PARENT.remove(cable);
		if (root == null) {
			return;
		}
		CableNetwork network = NETWORKS.get(root);
		if (network == null) {
			return;
		}
		// Whatever is left has to be re-discovered: the cable may have been the
		// bridge between two halves.
		network.markDirty();
		network.cables().remove(cable);

		if (network.cables().isEmpty()) {
			NETWORKS.remove(root);
			return;
		}
		if (root == cable) {
			CableBlockEntity newRoot = network.cables().iterator().next();
			NETWORKS.remove(root);
			NETWORKS.put(newRoot, network);
			for (CableBlockEntity member : network.cables()) {
				PARENT.put(member, newRoot);
			}
		}
	}

	/**
	 * @param cable {@link CableBlockEntity} the cable
	 * @return {@code boolean} whether the cable sits in a loaded chunk of a
	 *         server world
	 */
	static boolean isTicking(CableBlockEntity cable) {
		return cable.getWorld() instanceof ServerWorld serverWorld && serverWorld.isChunkLoaded(cable.getPos());
	}

	/**
	 * Union-find lookup with path compression.
	 *
	 * @param cable {@link CableBlockEntity} the cable
	 * @return {@link CableBlockEntity} the representative, or {@code null} when
	 *         the cable is not registered
	 */
	private static CableBlockEntity find(CableBlockEntity cable) {
		CableBlockEntity parent = PARENT.get(cable);
		if (parent == null) {
			return null;
		}
		if (parent == cable) {
			return cable;
		}
		CableBlockEntity root = find(parent);
		if (root != null) {
			PARENT.put(cable, root);
		}
		return root;
	}

	/**
	 * Re-discovers the component containing {@code seed} and rebuilds its
	 * union-find set, releasing every cable that used to belong to the same
	 * network but is no longer part of it (a removed cable, a split half, or one
	 * whose chunk unloaded).
	 *
	 * @param seed {@link CableBlockEntity} any member of the component
	 * @return {@link CableNetwork} the rebuilt network
	 */
	private static CableNetwork rebuild(CableBlockEntity seed) {
		CableNetwork previous = networkOf(seed);

		// Everything that has to be released: the old membership plus whatever
		// the BFS finds now. A cable may be in either but not both.
		Set<CableBlockEntity> affected = Collections.newSetFromMap(new IdentityHashMap<>());
		if (previous != null) {
			affected.addAll(previous.cables());
		}
		affected.add(seed);

		List<CableBlockEntity> members = discover(seed);
		affected.addAll(members);

		// Inherit the freshest processing stamp from every network being torn
		// down, not just the seed's. A freshly placed cable has no network of its
		// own, yet it can merge two networks that were both already processed
		// this tick; without this the merged network would run its transfers a
		// second time in the same tick.
		long lastTick = Long.MIN_VALUE;
		for (CableBlockEntity member : affected) {
			CableBlockEntity root = find(member);
			if (root == null) {
				continue;
			}
			CableNetwork existing = NETWORKS.get(root);
			if (existing != null) {
				lastTick = Math.max(lastTick, existing.lastTick());
			}
		}

		for (CableBlockEntity member : affected) {
			CableBlockEntity root = find(member);
			if (root != null) {
				CableNetwork network = NETWORKS.get(root);
				if (network != null) {
					network.cables().remove(member);
				}
			}
			PARENT.remove(member);
		}
		NETWORKS.values().removeIf(network -> network.cables().isEmpty());

		CableNetwork rebuilt = new CableNetwork();
		rebuilt.setLastTick(lastTick);
		rebuilt.setDirty(false);
		if (members.isEmpty()) {
			// The seed is not in a loaded chunk any more; nothing to register.
			return rebuilt;
		}

		for (CableBlockEntity member : members) {
			PARENT.put(member, seed);
			rebuilt.cables().add(member);
		}
		NETWORKS.put(seed, rebuilt);
		return rebuilt;
	}

	/**
	 * Breadth-first walk over every cable wired to {@code seed} with the same
	 * transfer rate, skipping cables in unloaded chunks.
	 *
	 * @param seed {@link CableBlockEntity} the starting cable
	 * @return {@link List} the members, {@code seed} first, empty when the seed
	 *         is not in a loaded chunk
	 */
	private static List<CableBlockEntity> discover(CableBlockEntity seed) {
		List<CableBlockEntity> members = new ArrayList<>();
		if (!isTicking(seed)) {
			return members;
		}

		Set<CableBlockEntity> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		Deque<CableBlockEntity> queue = new ArrayDeque<>();

		members.add(seed);
		seen.add(seed);
		queue.add(seed);

		while (!queue.isEmpty()) {
			CableBlockEntity current = queue.removeFirst();
			int transferRate = current.getCableType().transferRate;

			for (Direction direction : Direction.values()) {
				if (!(current.getAdjacentBlockEntity(direction) instanceof CableBlockEntity adjacent)) {
					continue;
				}
				if (adjacent.getCableType().transferRate != transferRate) {
					continue;
				}
				if (!seen.add(adjacent) || !isTicking(adjacent)) {
					continue;
				}
				members.add(adjacent);
				queue.add(adjacent);
			}
		}

		return members;
	}
}
