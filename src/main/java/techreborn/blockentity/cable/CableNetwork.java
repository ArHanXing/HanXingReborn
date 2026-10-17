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

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * One electrical network: the cables that are wired together plus the aggregate
 * values the union-find keeps for the component.
 * <p>
 * The aggregate is a snapshot of the last tick the network was processed by
 * {@link CableTickManager}, which is also what the Jade tooltip shows. A network
 * only exists while it has members; {@link CableNetworkManager} creates and
 * drops them as cables are placed, broken and re-discovered.
 * <p>
 * Identity, not equality: cables are compared by reference, so two block
 * entities that happen to be equal never share a set entry.
 */
public final class CableNetwork {

	private final Set<CableBlockEntity> cables = Collections.newSetFromMap(new IdentityHashMap<>());

	private long buffer;
	private long capacity;
	private long input;
	private long output;
	private long lastTick = Long.MIN_VALUE;
	/**
	 * {@code true} while the cached membership is known to be stale, e.g. after
	 * a neighbour changed or a member's chunk unloaded. The next
	 * {@link CableNetworkManager#resolve} re-discovers the component.
	 */
	private boolean dirty = true;
	/** {@code true} once a tick has completed and written a snapshot. */
	private boolean processed = false;

	Set<CableBlockEntity> cables() {
		return cables;
	}

	/**
	 * @return {@code int} how many cables the network is made of
	 */
	public int size() {
		return cables.size();
	}

	/**
	 * @return {@code long} energy currently stored across the whole network
	 */
	public long getBuffer() {
		return buffer;
	}

	/**
	 * @return {@code long} sum of the members' internal capacity
	 */
	public long getCapacity() {
		return capacity;
	}

	/**
	 * @return {@code long} energy pulled in from generators during the last
	 *         processed tick
	 */
	public long getInput() {
		return input;
	}

	/**
	 * @return {@code long} energy delivered to consumers during the last
	 *         processed tick
	 */
	public long getOutput() {
		return output;
	}

	long lastTick() {
		return lastTick;
	}

	void setLastTick(long lastTick) {
		this.lastTick = lastTick;
	}

	boolean isDirty() {
		return dirty;
	}

	void markDirty() {
		this.dirty = true;
	}

	void setDirty(boolean dirty) {
		this.dirty = dirty;
	}

	void setStats(long buffer, long capacity, long input, long output) {
		this.buffer = buffer;
		this.capacity = capacity;
		this.input = input;
		this.output = output;
		this.processed = true;
	}

	/**
	 * A network counts as idle once a completed tick moved no energy in either
	 * direction. It is only a hint used to skip the transfer phase: the pump
	 * still checks the buffered total, because a consumer that was full can
	 * become able to accept energy without any block event the cables can see.
	 *
	 * @return {@code boolean} whether the last processed tick moved no energy
	 */
	boolean isIdle() {
		return processed && input == 0 && output == 0;
	}

	/**
	 * @return {@link Collection} the members, for iteration during a tick
	 */
	Collection<CableBlockEntity> members() {
		return cables;
	}

	/**
	 * @return {@code boolean} whether every member still sits in a loaded chunk
	 */
	boolean allMembersLoaded() {
		for (CableBlockEntity cable : cables) {
			if (!CableNetworkManager.isTicking(cable)) {
				return false;
			}
		}
		return true;
	}
}
