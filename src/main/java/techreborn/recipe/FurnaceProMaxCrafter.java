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
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.SmeltingRecipe;
import reborncore.common.crafting.RebornRecipe;
import reborncore.common.crafting.SizedIngredient;
import reborncore.common.recipes.RecipeCrafter;
import reborncore.common.util.RebornInventory;
import techreborn.init.ModRecipes;

import java.util.ArrayList;
import java.util.List;

/**
 * Crafter for the Furnace Pro Max ("异步！并行？超泥土！熔炉"). Runs every
 * vanilla furnace (smelting) recipe, each wrapped as a
 * {@link VanillaSmeltingRecipe} so the base {@link RecipeCrafter} machinery
 * (parallelism, every-tick parallel refresh, Jade display, GUI progress)
 * applies unchanged.
 * <p>
 * Every recipe is forced to 1 tick / 1 EU by the wrapper; the machine itself
 * sets the maximum parallel count to {@link Integer#MAX_VALUE}.
 */
public class FurnaceProMaxCrafter extends RecipeCrafter {

	public FurnaceProMaxCrafter(BlockEntity blockEntity, int inputs, int outputs,
			RebornInventory<?> inventory, int[] inputSlots, int[] outputSlots) {
		super(ModRecipes.FURNACE_PRO_MAX, blockEntity, inputs, outputs, inventory, inputSlots, outputSlots);
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
			if (recipe.canCraft(blockEntity) && hasAllInputs(recipe, 1)) {
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
	 * matches, it becomes the current recipe.
	 *
	 * @param recipe {@link RebornRecipe} the candidate recipe
	 * @return {@code true} if the recipe was selected
	 */
	private boolean trySetRecipe(RebornRecipe recipe) {
		// Computes the parallel count (also checks inputs and output space)
		int parallel = getParallelCount(recipe);
		if (parallel <= 0) {
			return false;
		}
		if (!recipe.canCraft(blockEntity)) {
			return false;
		}
		setCurrentRecipe(recipe);
		this.currentParallelCount = parallel;
		this.currentNeededTicks = Math.max((int) (currentRecipe.time() * (1.0 - getSpeedMultiplier())), 1);
		setIsActive();
		return true;
	}

	/**
	 * Every vanilla smelting recipe, wrapped with the fixed 1 tick / 1 EU
	 * specification of this machine.
	 *
	 * @return {@link List} of candidate recipes
	 */
	private List<RebornRecipe> allRecipes() {
		List<RebornRecipe> result = new ArrayList<>();
		for (RecipeEntry<SmeltingRecipe> entry : blockEntity.getWorld().getRecipeManager()
				.getAllOfType(RecipeType.SMELTING)) {
			result.add(new VanillaSmeltingRecipe(entry.value(), blockEntity.getWorld()));
		}
		return result;
	}
}
