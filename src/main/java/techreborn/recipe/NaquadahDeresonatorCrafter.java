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
import techreborn.blockentity.machine.multiblock.NaquadahDeresonatorBlockEntity;
import techreborn.init.ModRecipes;

/**
 * Recipe crafter of the Naquadah Deresonator.
 * <p>
 * The only thing it adds over the standard crafter is the chance roll: the
 * deresonator resolves every run with a single roll instead of always
 * producing. The probability comes from the fluid the machine drained while the
 * run was in progress, so the crafter only has to delegate the decision.
 */
public class NaquadahDeresonatorCrafter extends RecipeCrafter {

	private final NaquadahDeresonatorBlockEntity machine;

	public NaquadahDeresonatorCrafter(NaquadahDeresonatorBlockEntity machine, RebornInventory<?> inventory,
			int[] inputSlots, int[] outputSlots) {
		super(ModRecipes.NAQUADAH_DERESONATOR, machine, 2, 4, inventory, inputSlots, outputSlots);
		this.machine = machine;
	}

	@Override
	protected boolean rollCraftSuccess() {
		return machine.rollCraftSuccess();
	}
}
