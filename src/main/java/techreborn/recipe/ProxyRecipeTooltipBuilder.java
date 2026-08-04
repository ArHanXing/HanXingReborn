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

package techreborn.recipe;

import net.minecraft.recipe.RecipeType;
import net.minecraft.registry.Registries;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import reborncore.common.crafting.RebornRecipe;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the tooltip lines shown on machines that run proxy recipes, e.g.:
 * <pre>
 * Available recipe types: Large Chemical Reactor, Chemical Reactor
 * Runs Chemical Reactor recipes at 0.8x power and 0.5x time.
 * </pre>
 * Machine names and multiplier values are highlighted in yellow.
 */
public final class ProxyRecipeTooltipBuilder {

	private ProxyRecipeTooltipBuilder() {
	}

	/**
	 * Appends the proxy recipe tooltip lines to the given tooltip list.
	 *
	 * @param tooltip         {@link List} the tooltip to append to
	 * @param ownType         {@link RecipeType} the machine's own recipe type
	 * @param proxyTypes      {@link List} of proxied (smaller machine) recipe types
	 * @param powerMultiplier {@code float} power multiplier applied to proxy recipes
	 * @param timeMultiplier  {@code float} time multiplier applied to proxy recipes
	 */
	public static void build(List<Text> tooltip, RecipeType<? extends RebornRecipe> ownType,
			List<RecipeType<? extends RebornRecipe>> proxyTypes, float powerMultiplier, float timeMultiplier) {
		if (proxyTypes.isEmpty()) {
			return;
		}

		// "Available recipe types: Own, Proxy"
		List<Text> allNames = new ArrayList<>();
		allNames.add(machineName(ownType));
		for (RecipeType<? extends RebornRecipe> proxyType : proxyTypes) {
			allNames.add(machineName(proxyType));
		}
		tooltip.add(Text.translatable("item.techreborn.multiblock_proxy.tooltip.types", joinNames(allNames)));

		// "Runs Proxy recipes at 0.8x power and 0.5x time."
		List<Text> proxyNames = new ArrayList<>();
		for (RecipeType<? extends RebornRecipe> proxyType : proxyTypes) {
			proxyNames.add(machineName(proxyType));
		}
		tooltip.add(Text.translatable("item.techreborn.multiblock_proxy.tooltip.running",
				joinNames(proxyNames), formatMultiplier(powerMultiplier), formatMultiplier(timeMultiplier)));
	}

	/**
	 * Resolves the display name of a machine from its recipe type id, e.g.
	 * {@code techreborn:chemical_reactor} -> {@code block.techreborn.chemical_reactor}.
	 */
	private static Text machineName(RecipeType<? extends RebornRecipe> type) {
		Identifier id = Registries.RECIPE_TYPE.getId(type);
		return Text.translatable("block." + id.getNamespace() + "." + id.getPath()).formatted(Formatting.YELLOW);
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
