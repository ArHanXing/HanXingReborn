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

package techreborn.blocks;

import net.minecraft.recipe.RecipeType;
import net.minecraft.registry.Registries;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import reborncore.common.crafting.RebornRecipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fluent builder for multiblock machine tooltips, decoupled from any specific
 * mechanism (proxy recipes, parallel, ...). Each machine block composes the
 * lines it wants:
 * <pre>
 * Available recipe types: Large Chemical Reactor, Chemical Reactor
 * Runs Chemical Reactor recipes at 0.8x power and 0.5x time.
 * Maximum parallel: 16
 * </pre>
 * Machine names and values are highlighted in yellow.
 */
public final class MultiblockTooltipBuilder {

	/**
	 * Recipe type ids whose machine display name uses a different block
	 * translation key than {@code block.&lt;ns&gt;.&lt;path&gt;}.
	 */
	private static final Map<Identifier, String> NAME_OVERRIDES = Map.of(
			Identifier.of("techreborn", "blast_furnace"), "block.techreborn.industrial_blast_furnace"
	);

	private final List<Text> lines = new ArrayList<>();

	private MultiblockTooltipBuilder() {
	}

	public static MultiblockTooltipBuilder create() {
		return new MultiblockTooltipBuilder();
	}

	/**
	 * Adds the "available recipe types" line listing the machine's own type
	 * followed by every proxied type. No-op when there are no proxy types.
	 */
	public MultiblockTooltipBuilder recipeTypes(RecipeType<? extends RebornRecipe> ownType,
			List<RecipeType<? extends RebornRecipe>> proxyTypes) {
		if (proxyTypes.isEmpty()) {
			return this;
		}
		List<Text> names = new ArrayList<>();
		names.add(machineName(ownType));
		proxyTypes.forEach(proxyType -> names.add(machineName(proxyType)));
		lines.add(Text.translatable("item.techreborn.multiblock_proxy.tooltip.types", joinNames(names)));
		return this;
	}

	/**
	 * Adds the "runs X recipes at Y power and Z time" line for the given proxy
	 * types. No-op when there are no proxy types.
	 */
	public MultiblockTooltipBuilder multipliers(List<RecipeType<? extends RebornRecipe>> proxyTypes,
			float powerMultiplier, float timeMultiplier) {
		if (proxyTypes.isEmpty()) {
			return this;
		}
		List<Text> names = new ArrayList<>();
		proxyTypes.forEach(proxyType -> names.add(machineName(proxyType)));
		lines.add(Text.translatable("item.techreborn.multiblock_proxy.tooltip.running",
				joinNames(names), formatMultiplier(powerMultiplier), formatMultiplier(timeMultiplier)));
		return this;
	}

	/**
	 * Adds a "maximum parallel: N" line.
	 */
	public MultiblockTooltipBuilder maxParallel(int maxParallel) {
		lines.add(Text.translatable("item.techreborn.multiblock_parallel.tooltip",
				Text.literal(String.valueOf(maxParallel)).formatted(Formatting.YELLOW)));
		return this;
	}

	/**
	 * Adds a custom line via a translation key (e.g. a special parallel rule).
	 */
	public MultiblockTooltipBuilder note(String translationKey) {
		lines.add(Text.translatable(translationKey));
		return this;
	}

	/**
	 * Adds an arbitrary raw text line.
	 */
	public MultiblockTooltipBuilder line(Text text) {
		lines.add(text);
		return this;
	}

	public void appendTo(List<Text> tooltip) {
		tooltip.addAll(lines);
	}

	/**
	 * Resolves the display name of a machine from its recipe type id, e.g.
	 * {@code techreborn:chemical_reactor} -> {@code block.techreborn.chemical_reactor}.
	 * Falls back to {@link #NAME_OVERRIDES} for recipe types whose machine block
	 * uses a different name (e.g. {@code blast_furnace} -> {@code industrial_blast_furnace}).
	 */
	private static Text machineName(RecipeType<? extends RebornRecipe> type) {
		Identifier id = Registries.RECIPE_TYPE.getId(type);
		String key = NAME_OVERRIDES.getOrDefault(id, "block." + id.getNamespace() + "." + id.getPath());
		return Text.translatable(key).formatted(Formatting.YELLOW);
	}

	/**
	 * Joins translated machine names with gray separators.
	 */
	private static Text joinNames(List<Text> names) {
		MutableText result = Text.literal("");
		for (int i = 0; i < names.size(); i++) {
			if (i > 0) {
				result.append(Text.literal(", ").formatted(Formatting.GRAY));
			}
			result.append(names.get(i));
		}
		return result;
	}

	private static Text formatMultiplier(float multiplier) {
		String value = multiplier == (int) multiplier ? String.valueOf((int) multiplier) : String.valueOf(multiplier);
		return Text.literal(value + "x").formatted(Formatting.YELLOW);
	}
}
