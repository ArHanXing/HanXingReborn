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

package techreborn.blockentity.generator;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import reborncore.common.powerSystem.PowerSystem;

import java.util.List;

/**
 * Shared tooltip lines of generators. The nominal generation rate is kept
 * apart from the energy block's {@code Max Output} line: burn- or fuel-based
 * generators often run below their output cap.
 */
public final class GeneratorTooltipHelper {

	private GeneratorTooltipHelper() {
	}

	/**
	 * Appends the "Generation rate (nominal)" line showing how much energy
	 * the generator produces per tick while it is actually generating.
	 */
	public static void addGenerationRate(List<Text> info, long euPerTick) {
		info.add(Text.translatable("techreborn.tooltip.generationRate.nominal")
				.formatted(Formatting.GRAY)
				.append(": ")
				.append(Text.literal(PowerSystem.getLocalizedPower(euPerTick)).formatted(Formatting.GOLD)));
	}
}
