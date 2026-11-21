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

package techreborn.utils;

/**
 * Turns how full a storage block is into a redstone comparator signal.
 * <p>
 * The one rule that matters: <b>15 means full and nothing else does</b>. A
 * storage that is one item short of its capacity must still read 14, otherwise a
 * comparator cannot be used to switch a machine off exactly when the tank fills.
 * The same goes for the other end: 0 means empty, and any content at all reads at
 * least 1.
 * <p>
 * Between those two ends the remaining 14 levels are spread evenly over the
 * partial range, so each level covers an equal share of the capacity.
 */
public final class StorageComparator {

	/** Highest comparator signal. */
	public static final int MAX_SIGNAL = 15;

	private StorageComparator() {
	}

	/**
	 * @param amount   {@code long} how much is stored right now
	 * @param capacity {@code long} how much it can hold
	 * @return {@code int} the comparator signal, {@code 0} for empty and {@code 15}
	 *         only when completely full
	 */
	public static int signal(long amount, long capacity) {
		if (capacity <= 0 || amount <= 0) {
			return 0;
		}
		if (amount >= capacity) {
			return MAX_SIGNAL;
		}

		// Partial range: map (0, capacity) onto 1..14. The clamp keeps a rounding
		// result of 14 from turning into 15, which would break the "15 iff full"
		// guarantee for the last level.
		int partial = (int) (14.0 * (double) amount / (double) capacity);
		return 1 + Math.min(MAX_SIGNAL - 2, Math.max(0, partial));
	}
}
