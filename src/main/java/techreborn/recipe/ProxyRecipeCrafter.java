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

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeType;
import reborncore.common.crafting.RebornRecipe;
import reborncore.common.crafting.RecipeUtils;
import reborncore.common.crafting.SizedIngredient;
import reborncore.common.recipes.RecipeCrafter;
import reborncore.common.util.RebornInventory;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link RecipeCrafter} that can also run recipes from one or more other
 * (proxy) recipe types.
 * <p>
 * Example: the Large Chemical Reactor runs its own
 * {@code LARGE_CHEMICAL_REACTOR} recipes plus every {@code CHEMICAL_REACTOR}
 * (small reactor) recipe. Proxy recipes are wrapped in a {@link ProxyRecipe}
 * so their time and power can be multiplied (e.g. 0.5x time, 0.8x power).
 * <p>
 * Own recipes always take priority over proxy recipes.
 */
public class ProxyRecipeCrafter extends RecipeCrafter {

	private final List<RecipeType<? extends RebornRecipe>> proxyTypes;
	private final float timeMultiplier;
	private final float powerMultiplier;

	public ProxyRecipeCrafter(RecipeType<? extends RebornRecipe> recipeType,
			List<RecipeType<? extends RebornRecipe>> proxyTypes,
			float timeMultiplier, float powerMultiplier,
			BlockEntity blockEntity, int inputs, int outputs,
			RebornInventory<?> inventory, int[] inputSlots, int[] outputSlots) {
		super(recipeType, blockEntity, inputs, outputs, inventory, inputSlots, outputSlots);
		this.proxyTypes = proxyTypes;
		this.timeMultiplier = timeMultiplier;
		this.powerMultiplier = powerMultiplier;
	}

	@Override
	public void updateCurrentRecipe() {
		currentTickTime = 0;
		for (RebornRecipe recipe : allRecipes()) {
			if (trySetRecipe(recipe)) {
				return;
			}
		}
		setCurrentRecipe(null);
		currentNeededTicks = 0;
		setIsActive();
	}

	@Override
	public boolean canCraftAgain() {
		for (RebornRecipe recipe : allRecipes()) {
			if (recipe.canCraft(blockEntity) && hasAllInputs(recipe)) {
				final List<ItemStack> outputs = recipe.outputs();
				for (int i = 0; i < outputs.size(); i++) {
					if (!canFitOutput(outputs.get(i), outputSlots[i])) {
						return false;
					}
				}
				return !(energy.getEnergy() < recipe.power());
			}
		}
		return false;
	}

	@Override
	public boolean isStackValidInput(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		// Test with a stack with the max stack size as some independents will check the stack size.
		ItemStack largeStack = stack.copy();
		largeStack.setCount(largeStack.getMaxCount());
		for (RebornRecipe recipe : allRecipes()) {
			for (SizedIngredient ingredient : recipe.ingredients()) {
				if (ingredient.test(largeStack)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Matches the given recipe against the inventory and output space. If it
	 * matches, it becomes the current recipe (own type first, proxy types after).
	 *
	 * @param recipe {@link RebornRecipe} the candidate recipe
	 * @return {@code true} if the recipe was selected
	 */
	private boolean trySetRecipe(RebornRecipe recipe) {
		if (!hasAllInputs(recipe)) {
			return false;
		}
		if (!recipe.canCraft(blockEntity)) {
			return false;
		}
		final List<ItemStack> outputs = recipe.outputs();
		for (int i = 0; i < outputs.size(); i++) {
			if (!canFitOutput(outputs.get(i), outputSlots[i])) {
				return false;
			}
		}
		setCurrentRecipe(recipe);
		this.currentNeededTicks = Math.max((int) (currentRecipe.time() * (1.0 - getSpeedMultiplier())), 1);
		setIsActive();
		return true;
	}

	/**
	 * All runnable recipes: own type first, then every proxy type wrapped with
	 * the configured time/power multipliers.
	 *
	 * @return {@link List} of candidate recipes
	 */
	private List<RebornRecipe> allRecipes() {
		List<RebornRecipe> result = new ArrayList<>(RecipeUtils.getRecipes(blockEntity.getWorld(), recipeType));
		for (RecipeType<? extends RebornRecipe> proxyType : proxyTypes) {
			for (RebornRecipe recipe : RecipeUtils.getRecipes(blockEntity.getWorld(), proxyType)) {
				result.add(new ProxyRecipe(recipe, timeMultiplier, powerMultiplier));
			}
		}
		return result;
	}
}
