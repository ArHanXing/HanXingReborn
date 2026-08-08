/*
 * This file is part of TechReborn, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2026 TechReborn
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
import net.minecraft.recipe.RecipeType;
import reborncore.common.crafting.RebornRecipe;
import reborncore.common.util.RebornInventory;

import java.util.List;

/**
 * Recipe crafter used by the Ore Crusher and the Large Ore Crusher.
 * <p>
 * These machines turn stone-like inputs (cobblestone, obsidian, granite,
 * diorite, andesite, end stone) into 16 stone per run, WITHOUT consuming the
 * input item: the input acts as a permanent catalyst. The normal craft flow
 * still produces the output; only the consumption step is disabled by
 * overriding {@link #useAllInputs()} with a no-op.
 * <p>
 * Like {@link ProxyRecipeCrafter}, the large variant can run the small
 * machine's recipes at a configurable time/power multiplier.
 */
public class OreCrusherRecipeCrafter extends ProxyRecipeCrafter {

	public OreCrusherRecipeCrafter(RecipeType<? extends RebornRecipe> recipeType,
			List<RecipeType<? extends RebornRecipe>> proxyTypes,
			float timeMultiplier, float powerMultiplier,
			BlockEntity blockEntity, int inputs, int outputs,
			RebornInventory<?> inventory, int[] inputSlots, int[] outputSlots) {
		super(recipeType, proxyTypes, timeMultiplier, powerMultiplier,
				blockEntity, inputs, outputs, inventory, inputSlots, outputSlots);
	}

	/**
	 * Ore crushers never consume their input: the catalyst item stays in the
	 * input slot forever while stone keeps being produced.
	 */
	@Override
	public void useAllInputs() {
		// Intentionally empty: input is not consumed.
	}
}
