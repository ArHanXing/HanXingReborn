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

package techreborn.client.compat.igi;

import df11zomgraves.ingameinfo.tag.Tag;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.text.Text;
import reborncore.common.powerSystem.PowerSystem;

/**
 * InGame Info XML tags exposing the bound cable network.
 * <p>
 * Each tag reads {@link EnergyBindClientState} only: the values are pushed by the
 * server a few times a second, and {@code getValue()} runs per frame so it must
 * stay allocation-light and never touch the world.
 * <p>
 * Every tag degrades to a red fallback rather than to a bare zero, so a wrong or
 * missing binding is visible instead of looking like an idle network.
 */
@Environment(EnvType.CLIENT)
public final class EnergyBindTags {

	/** Red text, used for every "no data" case. */
	private static final String RED = "§c";

	private EnergyBindTags() {
	}

	/**
	 * @return {@link String} the red fallback for the current state, or
	 *         {@code null} when there is valid data
	 */
	private static String fallback() {
		if (!EnergyBindClientState.isBound()) {
			return RED + Text.translatable("techreborn.igi.unbound").getString();
		}
		if (!EnergyBindClientState.isValid()) {
			return RED + Text.translatable("techreborn.igi.invalid").getString();
		}
		return null;
	}

	private static String power(long value) {
		return PowerSystem.getLocalizedPowerNoSuffix(value);
	}

	/** 5s average input, EU/t. */
	public static final class AverageInput extends Tag {
		@Override
		public String getCategory() {
			return "techreborn";
		}

		@Override
		public String getValue() {
			String fallback = fallback();
			return fallback != null ? fallback : power(EnergyBindClientState.getAverageInput());
		}
	}

	/** 5s average output, EU/t. */
	public static final class AverageOutput extends Tag {
		@Override
		public String getCategory() {
			return "techreborn";
		}

		@Override
		public String getValue() {
			String fallback = fallback();
			return fallback != null ? fallback : power(EnergyBindClientState.getAverageOutput());
		}
	}

	/** Current buffer, or buffer / capacity. */
	public static final class Buffer extends Tag {
		@Override
		public String getCategory() {
			return "techreborn";
		}

		@Override
		public String getValue() {
			String fallback = fallback();
			return fallback != null ? fallback
				: power(EnergyBindClientState.getBuffer()) + " / " + power(EnergyBindClientState.getCapacity());
		}
	}

	/** Current buffer as a percentage of capacity. */
	public static final class BufferPercent extends Tag {
		@Override
		public String getCategory() {
			return "techreborn";
		}

		@Override
		public String getValue() {
			String fallback = fallback();
			if (fallback != null) {
				return fallback;
			}
			long capacity = EnergyBindClientState.getCapacity();
			if (capacity <= 0) {
				return RED + "-";
			}
			return String.format("%.0f%%", EnergyBindClientState.getBuffer() * 100.0 / capacity);
		}
	}

	/**
	 * Time until the network empties or fills, whichever the current net flow is
	 * heading towards.
	 */
	public static final class TimeToEdge extends Tag {
		@Override
		public String getCategory() {
			return "techreborn";
		}

		@Override
		public String getValue() {
			String fallback = fallback();
			if (fallback != null) {
				return fallback;
			}
			long ticks = EnergyBindClientState.getTicksToEdge();
			if (ticks == -1) {
				// Already at the edge; the direction is meaningless here.
				return RED + Text.translatable("techreborn.igi.at_edge").getString();
			}
			if (ticks == Long.MAX_VALUE) {
				// Net flow is zero, so the level never moves.
				return RED + Text.translatable("techreborn.igi.stable").getString();
			}
			boolean draining = EnergyBindClientState.getAverageOutput() > EnergyBindClientState.getAverageInput();
			String key = draining ? "techreborn.igi.empty_in" : "techreborn.igi.full_in";
			return Text.translatable(key, formatTicks(ticks)).getString();
		}

		private static String formatTicks(long ticks) {
			long seconds = Math.max(0, ticks) / 20;
			if (seconds >= 3600) {
				return String.format("%.1fh", seconds / 3600.0);
			}
			if (seconds >= 60) {
				return String.format("%.1fm", seconds / 60.0);
			}
			return seconds + "s";
		}
	}
}
