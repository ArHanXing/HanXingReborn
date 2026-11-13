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
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.List;

/**
 * Hand-authored CLIENT PREVIEW catalogue. Resource stacks, measurements and
 * markers are illustrative UI samples, never authoritative mission data.
 * Nothing in this class registers content or reads/writes team progression.
 */
public final class SpacePreviewCatalog {

	private static final List<SpaceBody> BODIES = List.of(
			body("sol:sun", "sol", null, "sun", "star", 1, Blocks.GLOWSTONE, 0, 0, 2.1, 696340, 5772),
			body("sol:mercury", "sol", "sol:sun", "mercury", "molten", 1, Blocks.MAGMA_BLOCK, .387, 20, .7, 2439.7, 440),
			body("sol:venus", "sol", "sol:sun", "venus", "toxic", 1, Blocks.YELLOW_TERRACOTTA, .723, 68, .95, 6051.8, 737),
			body("sol:earth", "sol", "sol:sun", "earth", "continental", 1, Blocks.MOSS_BLOCK, 1, 155, 1, 6371, 288),
			body("sol:moon", "sol", "sol:earth", "moon", "barren", 1, Blocks.END_STONE, .00257, 42, .65, 1737.4, 220),
			body("sol:mars", "sol", "sol:sun", "mars", "barren", 1, Blocks.RED_SAND, 1.524, 244, .85, 3389.5, 210),
			body("sol:asteroid_belt", "sol", "sol:sun", "asteroid_belt", "asteroid_belt", 1, Blocks.STONE, 2.8, 310, .65, 0, 170),
			body("sol:jupiter", "sol", "sol:sun", "jupiter", "gas_giant", 2, Blocks.ORANGE_TERRACOTTA, 5.203, 15, 1.8, 69911, 165),
			body("sol:io", "sol", "sol:jupiter", "io", "molten", 2, Blocks.YELLOW_CONCRETE, .00282, 100, .55, 1821.6, 110),
			body("sol:saturn", "sol", "sol:sun", "saturn", "gas_giant", 2, Blocks.SANDSTONE, 9.537, 110, 1.6, 58232, 134),
			body("sol:enceladus", "sol", "sol:saturn", "enceladus", "frozen", 2, Blocks.SNOW_BLOCK, .00159, 25, .5, 252.1, 75),
			body("sol:titan", "sol", "sol:saturn", "titan", "toxic", 2, Blocks.BROWN_TERRACOTTA, .00817, 165, .6, 2574.7, 94),
			body("sol:iapetus", "sol", "sol:saturn", "iapetus", "frozen", 2, Blocks.CALCITE, .0238, 290, .5, 734.5, 110),
			body("sol:uranus", "sol", "sol:sun", "uranus", "gas_giant", 2, Blocks.LIGHT_BLUE_CONCRETE, 19.191, 218, 1.4, 25362, 76),
			body("sol:ariel", "sol", "sol:uranus", "ariel", "frozen", 2, Blocks.PACKED_ICE, .00128, 60, .5, 578.9, 60),
			body("sol:neptune", "sol", "sol:sun", "neptune", "gas_giant", 2, Blocks.BLUE_CONCRETE, 30.07, 308, 1.4, 24622, 72),
			body("sol:triton", "sol", "sol:neptune", "triton", "frozen", 2, Blocks.BLUE_ICE, .00237, 145, .55, 1353.4, 38),
			body("ross128:star", "ross128", null, "ross128", "star", 3, Blocks.REDSTONE_BLOCK, 0, 0, 1.7, 136000, 3192),
			body("ross128:b", "ross128", "ross128:star", "ross128_b", "continental", 3, Blocks.MOSS_BLOCK, .0496, 145, 1, 7000, 294),
			body("ross128:ba", "ross128", "ross128:b", "ross128_ba", "frozen", 3, Blocks.PACKED_ICE, .0012, 310, .6, 1900, 180),
			body("vega1:star", "vega1", null, "vega1", "star", 3, Blocks.SEA_LANTERN, 0, 0, 2.2, 1640000, 9602),
			body("vega1:b", "vega1", "vega1:star", "vega1_b", "ocean", 3, Blocks.PRISMARINE, 2.5, 110, 1.2, 9200, 285),
			body("gliese581:star", "gliese581", null, "gliese581", "star", 3, Blocks.ORANGE_CONCRETE, 0, 0, 1.6, 208000, 3498),
			body("gliese581:g", "gliese581", "gliese581:star", "gliese581_g", "continental", 3, Blocks.AMETHYST_BLOCK, .146, 225, 1.1, 8500, 250),
			body("tau_ceti:star", "tau_ceti", null, "tau_ceti", "star", 3, Blocks.GLOWSTONE, 0, 0, 1.9, 550000, 5344),
			body("tau_ceti:e", "tau_ceti", "tau_ceti:star", "tau_ceti_e", "ocean", 3, Blocks.DARK_PRISMARINE, .538, 75, 1.1, 10300, 290),
			body("amon_ra:star", "amon_ra", null, "amon_ra", "star", 3, Blocks.OCHRE_FROGLIGHT, 0, 0, 2, 750000, 6200),
			body("amon_ra:maihan_belt", "amon_ra", "amon_ra:star", "maihan_belt", "asteroid_belt", 3, Blocks.END_STONE, 3.2, 50, .8, 0, 90),
			body("great_wound:black_hole", "great_wound", null, "great_wound", "black_hole", 3, Blocks.OBSIDIAN, 0, 0, 1.7, 0, 0)
	);
	private static final List<String> SYSTEMS = BODIES.stream().map(SpaceBody::system).distinct().toList();

	private SpacePreviewCatalog() {
	}

	public static List<SpaceBody> bodies() {
		return BODIES;
	}

	public static List<String> systems() {
		return SYSTEMS;
	}

	private static SpaceBody body(String id, String system, String parent, String key, String type,
			int tier, Block marker, double orbit, double phase, double scale, double radius, double temperature) {
		List<ItemStack> resources = type.equals("star") ? List.of() : List.of(
				new ItemStack(Items.RAW_IRON, 20), new ItemStack(Items.RAW_COPPER, 15),
				new ItemStack(Items.RAW_GOLD, 10), new ItemStack(Items.REDSTONE, 30),
				new ItemStack(Items.LAPIS_LAZULI, 10));
		return new SpaceBody(id, system, parent, "gui.techreborn.space.body." + key, type, tier,
				marker, orbit, phase, scale, resources, radius, temperature);
	}
}
