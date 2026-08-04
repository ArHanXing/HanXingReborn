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

import reborncore.common.crafting.RebornRecipe;
import reborncore.common.util.RebornInventory;
import techreborn.blockentity.machine.multiblock.RotaryHearthFurnaceBlockEntity;
import techreborn.init.ModRecipes;
import techreborn.recipe.recipes.BlastFurnaceRecipe;

import java.util.List;

/**
 * Recipe crafter for the Rotary Hearth Furnace (RHF).
 * <p>
 * Runs every Industrial Blast Furnace recipe at 0.5x time and 0.5x power, and
 * applies the RHF's special parallel rule: the base parallel count is 4, and
 * every 1000 heat above the recipe's requirement multiplies it by 4
 * (e.g. 4 - 16 - 64 - ...).
 */
public class RhfRecipeCrafter extends ProxyRecipeCrafter {

	private final RotaryHearthFurnaceBlockEntity machine;

	/** Base parallel count when heat just meets the recipe requirement. */
	public static final int BASE_PARALLEL = 4;

	/** Every 1000 heat above the requirement multiplies the parallel count by this. */
	public static final int HEAT_STEP = 1000;
	public static final int HEAT_PARALLEL_MULTIPLIER = 4;

	public RhfRecipeCrafter(RotaryHearthFurnaceBlockEntity machine,
			RebornInventory<?> inventory, int[] inputSlots, int[] outputSlots) {
		super(ModRecipes.ROTARY_HEARTH_FURNACE, List.of(ModRecipes.BLAST_FURNACE),
				0.5F, 0.5F, machine, 2, 2, inventory, inputSlots, outputSlots);
		this.machine = machine;
	}

	/**
	 * Computes the RHF parallel count:
	 * {@code BASE_PARALLEL * HEAT_PARALLEL_MULTIPLIER^tiers} where
	 * {@code tiers = max(0, (heat - requiredHeat) / HEAT_STEP)}, then limited
	 * by inputs and outputs via the base implementation.
	 */
	@Override
	protected int getParallelCount(RebornRecipe recipe) {
		int requiredHeat = 0;
		if (recipe instanceof ProxyRecipe proxy && proxy.delegate() instanceof BlastFurnaceRecipe blastFurnaceRecipe) {
			requiredHeat = blastFurnaceRecipe.getHeat();
		}

		int heat = machine.getHeat();
		int tiers = Math.max(0, (heat - requiredHeat) / HEAT_STEP);
		int parallel = BASE_PARALLEL;
		for (int i = 0; i < tiers; i++) {
			parallel *= HEAT_PARALLEL_MULTIPLIER;
		}

		setMaxParallel(parallel);
		return super.getParallelCount(recipe);
	}
}
