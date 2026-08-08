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

import reborncore.common.recipes.RecipeCrafter;
import reborncore.common.util.RebornInventory;
import techreborn.blockentity.machine.multiblock.IndustrialBlastFurnaceBlockEntity;
import techreborn.init.ModRecipes;
import techreborn.recipe.recipes.BlastFurnaceRecipe;

/**
 * Recipe crafter for the Industrial Blast Furnace (EBF).
 * <p>
 * Heat bonus: every 1000 heat above the recipe's requirement multiplies the
 * crafting time by {@link #TIME_FACTOR_PER_TIER} (e.g. x1.0 - x0.8 - x0.64 - ...).
 * The speed provided by machine upgrades still applies on top of the heat
 * bonus, and recipes whose heat requirement is not met are already rejected
 * by {@link BlastFurnaceRecipe#canCraft}.
 */
public class BlastFurnaceRecipeCrafter extends RecipeCrafter {

	/** Every 1000 heat above the requirement adds one time-reduction tier. */
	public static final int HEAT_STEP = 1000;

	/** Each tier multiplies the crafting time by this. */
	public static final double TIME_FACTOR_PER_TIER = 0.8;

	private final IndustrialBlastFurnaceBlockEntity machine;

	public BlastFurnaceRecipeCrafter(IndustrialBlastFurnaceBlockEntity machine,
			RebornInventory<?> inventory, int[] inputSlots, int[] outputSlots) {
		super(ModRecipes.BLAST_FURNACE, machine, 2, 2, inventory, inputSlots, outputSlots);
		this.machine = machine;
	}

	/**
	 * Recomputes the needed ticks after the base implementation, applying the
	 * heat time bonus: {@code baseNeededTicks * 0.8^tiers} with
	 * {@code tiers = max(0, (heat - requiredHeat) / HEAT_STEP)}.
	 */
	@Override
	public void updateCurrentRecipe() {
		super.updateCurrentRecipe();
		if (currentRecipe == null) {
			return;
		}

		int requiredHeat = 0;
		if (currentRecipe instanceof BlastFurnaceRecipe blastFurnaceRecipe) {
			requiredHeat = blastFurnaceRecipe.getHeat();
		}

		int tiers = Math.max(0, (machine.getHeat() - requiredHeat) / HEAT_STEP);
		double factor = Math.pow(TIME_FACTOR_PER_TIER, tiers);
		currentNeededTicks = Math.max((int) (currentNeededTicks * factor), 1);
	}
}
