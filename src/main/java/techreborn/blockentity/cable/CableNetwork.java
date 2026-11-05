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
	 * Sliding window of the last five seconds of throughput, one slot per tick.
	 * 100 ticks at 20 tps is the 5s average reported by the InGame Info XML tag.
	 */
	private static final int AVG_WINDOW_TICKS = 100;
	private final long[] inputWindow = new long[AVG_WINDOW_TICKS];
	private final long[] outputWindow = new long[AVG_WINDOW_TICKS];
	private int windowIndex = 0;
	private int windowFilled = 0;
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

	/**
	 * @return {@code long} mean energy pulled in per tick over the last five
	 *         seconds
	 */
	public long getAverageInput() {
		return average(inputWindow);
	}

	/**
	 * @return {@code long} mean energy delivered per tick over the last five
	 *         seconds
	 */
	public long getAverageOutput() {
		return average(outputWindow);
	}

	/**
	 * @return {@code long} mean net change per tick over the last five seconds
	 */
	public long getAverageNet() {
		return getAverageInput() - getAverageOutput();
	}

	/**
	 * Ticks until the buffer empties (net negative) or fills (net positive).
	 *
	 * @return {@code long} ticks until empty or full; {@code -1} when it is
	 *         already at that edge; {@code Long.MAX_VALUE} when the net flow is
	 *         zero and the level therefore never moves
	 */
	public long ticksUntilBufferEdge() {
		long net = getAverageNet();
		if (net == 0) {
			return Long.MAX_VALUE;
		}
		if (net < 0) {
			if (buffer <= 0) {
				return -1;
			}
			return buffer / -net;
		}
		long free = capacity - buffer;
		if (free <= 0) {
			return -1;
		}
		return free / net;
	}

	private long average(long[] window) {
		if (windowFilled == 0) {
			return 0;
		}
		long sum = 0;
		for (int i = 0; i < windowFilled; i++) {
			sum += window[i];
		}
		return sum / windowFilled;
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

		inputWindow[windowIndex] = input;
		outputWindow[windowIndex] = output;
		windowIndex = (windowIndex + 1) % AVG_WINDOW_TICKS;
		if (windowFilled < AVG_WINDOW_TICKS) {
			windowFilled++;
		}
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
