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
import techreborn.blockentity.machine.multiblock.LargeHeatSinkBlockEntity;
import techreborn.init.ModRecipes;

/**
 * Recipe crafter of the Large Heat Sink.
 * <p>
 * Everything specific to the machine lives in {@link
 * techreborn.recipe.recipes.LargeHeatSinkRecipe} (coolant accounting, parallel
 * limits, product deposit), so this is only the plain crafter bound to that
 * recipe type.
 */
public class HeatSinkRecipeCrafter extends RecipeCrafter {

	public HeatSinkRecipeCrafter(LargeHeatSinkBlockEntity machine, RebornInventory<?> inventory,
			int[] inputSlots, int[] outputSlots) {
		super(ModRecipes.LARGE_HEAT_SINK, machine, inputSlots.length, outputSlots.length, inventory,
			inputSlots, outputSlots);
	}
}
