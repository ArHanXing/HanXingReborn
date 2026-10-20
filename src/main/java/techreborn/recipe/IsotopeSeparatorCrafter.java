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
import reborncore.common.crafting.SizedIngredient;
import reborncore.common.recipes.RecipeCrafter;
import reborncore.common.util.RebornInventory;
import techreborn.blockentity.machine.multiblock.IsotopeSeparatorBlockEntity;
import techreborn.init.ModRecipes;

import java.util.List;

/**
 * Recipe crafter of the Isotope Separator.
 * <p>
 * Two things set it apart from the standard crafter:
 * <ul>
 *     <li>each attempt is resolved by a chance roll whose odds fall as the
 *     rotor spins faster ({@link IsotopeSeparatorBlockEntity#successRate});</li>
 *     <li>a failed attempt returns its feed unchanged, the way a Dyson Sphere
 *     fractionator does, so the material is only delayed and the output can be
 *     piped back into the input.</li>
 * </ul>
 */
public class IsotopeSeparatorCrafter extends RecipeCrafter {

	private final IsotopeSeparatorBlockEntity machine;

	public IsotopeSeparatorCrafter(IsotopeSeparatorBlockEntity machine, RebornInventory<?> inventory,
			int[] inputSlots, int[] outputSlots) {
		super(ModRecipes.ISOTOPE_SEPARATOR, machine, 1, 2, inventory, inputSlots, outputSlots);
		this.machine = machine;
	}

	@Override
	protected boolean rollCraftSuccess() {
		return machine.rollCraftSuccess();
	}

	/**
	 * Hands the feed back untouched into the tails slot.
	 * <p>
	 * Tails rather than the product slot on purpose: the product stream then
	 * only ever holds separated material, so a loop can pull the tails, feed
	 * them back in and let the machine retry, exactly like a fractionator. Only
	 * as much as the run would consume is returned, so a half-used input stack
	 * is not duplicated.
	 */
	@Override
	protected List<ItemStack> failureOutputs() {
		if (currentRecipe == null || outputSlots.length < 2) {
			return List.of();
		}
		ItemStack feed = inventory.getStack(inputSlots[0]);
		if (feed.isEmpty()) {
			return List.of();
		}
		int consumed = 0;
		for (SizedIngredient ingredient : currentRecipe.ingredients()) {
			consumed += ingredient.count();
		}
		consumed *= Math.max(currentParallelCount, 1);
		if (consumed <= 0) {
			return List.of();
		}
		ItemStack returned = feed.copy();
		returned.setCount(Math.min(consumed, feed.getCount()));
		// Index 0 is the product slot, 1 the tails slot.
		return List.of(ItemStack.EMPTY, returned);
	}
}
