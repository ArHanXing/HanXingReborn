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

package techreborn.space.data;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Validated server data, independent of a team's progress or a running mission.
 * IDs are derived from data/{namespace}/techreborn/planet/{path}.json.
 * Mutable stacks are never exposed by reference, including from list accessors.
 */
public record PlanetDefinition(Identifier id, String type, int tier, String system, Identifier fromSystem,
		@Nullable Identifier parent, int sortOrder, Display display, Block markerBlock, double markerScale,
		int time, Physical physical, Missions missions, List<Resource> resources, Visibility visibility,
		@Nullable Identifier dimension, List<String> tags) {

	public PlanetDefinition {
		Objects.requireNonNull(id);
		resources = List.copyOf(resources);
		tags = List.copyOf(tags);
	}

	public record Display(String nameKey, String descriptionKey) {
	}

	/** Orbit phase is resolved to a stable value during parsing, even if omitted. */
	public record Physical(double radiusKm, double orbitRadiusAu, double orbitPhaseDeg, double temperatureK,
			double rotationPeriodHours, double orbitalPeriodDays, List<String> orbitFeatures) {
		public Physical {
			orbitFeatures = List.copyOf(orbitFeatures);
		}
	}

	public record Missions(Stage survey, Stage land, Stage outpost, Mining mining) {
	}

	/** time is the unaccelerated stage duration in ticks. */
	public record Stage(boolean enabled, int time, List<Variant> variants) {
		public Stage {
			variants = List.copyOf(variants);
		}
	}

	/** Mining uses the planet's top-level time, not a second configurable multiplier. */
	public record Mining(boolean enabled, boolean requiresLanding, List<Variant> variants) {
		public Mining {
			variants = List.copyOf(variants);
		}
	}

	/** Exact item inputs/returns of one equipment tier; no implicit replacement. */
	public record Variant(int tier, List<ItemStack> inputs, List<ItemStack> outputs) {
		public Variant {
			inputs = copyStacks(inputs);
			outputs = copyStacks(outputs);
		}

		@Override
		public List<ItemStack> inputs() {
			return copyStacks(inputs);
		}

		@Override
		public List<ItemStack> outputs() {
			return copyStacks(outputs);
		}
	}

	/** id is a stable path within this planet, separate from the item registry ID. */
	public record Resource(String id, ItemStack stack, int order) {
		public Resource {
			stack = stack.copy();
		}

		@Override
		public ItemStack stack() {
			return stack.copy();
		}
	}

	/** Values are validated wire/data names: always, after_survey, after_land. */
	public record Visibility(String name, String physical, String resources) {
	}

	private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
		return stacks.stream().map(ItemStack::copy).toList();
	}
}
