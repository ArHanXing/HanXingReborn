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

import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.SmeltingRecipe;
import net.minecraft.world.World;
import reborncore.common.crafting.RebornRecipe;
import reborncore.common.crafting.SizedIngredient;

import java.util.List;

/**
 * Wraps a vanilla {@link SmeltingRecipe} as a {@link RebornRecipe} so the
 * regular RebornCore crafter machinery (parallelism, Jade display, etc.) can
 * run vanilla furnace recipes.
 * <p>
 * Time is forced to 1 tick and power to 1 EU per recipe run — this is the
 * Furnace Pro Max ("异步！并行？超泥土！熔炉") performance-test machine's
 * specification.
 */
public class VanillaSmeltingRecipe implements RebornRecipe {

	private final List<SizedIngredient> ingredients;
	private final List<ItemStack> outputs;

	public VanillaSmeltingRecipe(SmeltingRecipe delegate, World world) {
		this.ingredients = delegate.getIngredients().stream()
				.map(ingredient -> new SizedIngredient(1, ingredient))
				.toList();
		this.outputs = List.of(delegate.getResult(world.getRegistryManager()));
	}

	@Override
	public RecipeType<?> type() {
		return RecipeType.SMELTING;
	}

	@Override
	public List<SizedIngredient> ingredients() {
		return ingredients;
	}

	@Override
	public List<ItemStack> outputs() {
		return outputs;
	}

	@Override
	public int power() {
		return 1;
	}

	@Override
	public int time() {
		return 1;
	}
}
