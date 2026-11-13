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

package techreborn.client.gui.space;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Objects;

/**
 * Client presentation data only. This record grants no discovery, produces no
 * resources and is not a server/datapack definition. Parent is the sole source
 * of orbital relationships; system is only a page/group identifier.
 */
public record SpaceBody(String id, String system, String parent, String nameKey, String type,
		int tier, Block marker, double orbitRadiusAu, double phaseDegrees, double markerScale,
		List<ItemStack> resources, double radiusKm, double temperatureK) {

	public SpaceBody {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(system, "system");
		Objects.requireNonNull(nameKey, "nameKey");
		Objects.requireNonNull(type, "type");
		Objects.requireNonNull(marker, "marker");
		resources = resources.stream().map(ItemStack::copy).toList();
		if (tier < 1 || tier > 3 || !Double.isFinite(orbitRadiusAu) || orbitRadiusAu < 0
				|| !Double.isFinite(phaseDegrees) || !Double.isFinite(markerScale) || markerScale <= 0) {
			throw new IllegalArgumentException("Invalid client preview body: " + id);
		}
	}

	/** Presentation-only constructor for callers without physical measurements. */
	public SpaceBody(String id, String system, String parent, String nameKey, String type,
			int tier, Block marker, double orbitRadiusAu, double phaseDegrees, double markerScale,
			List<ItemStack> resources) {
		this(id, system, parent, nameKey, type, tier, marker, orbitRadiusAu, phaseDegrees,
				markerScale, resources, 0, 0);
	}

	public Text name() {
		return Text.translatable(nameKey);
	}

	/** ItemStack is mutable, so consumers receive independent preview stacks. */
	@Override
	public List<ItemStack> resources() {
		return resources.stream().map(ItemStack::copy).toList();
	}
}
