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

package techreborn.client.compat.emi;

import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.widget.WidgetHolder;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.recipe.RecipeEntry;
import reborncore.common.fluid.FluidValue;
import techreborn.blockentity.machine.multiblock.NaquadahDeresonatorBlockEntity;
import techreborn.recipe.recipes.NaquadahDeresonatorRecipe;

public class NaquadahDeresonatorEmiRecipe extends TREmiRecipe<NaquadahDeresonatorRecipe> {

	/**
	 * The fluid is drawn as one success step (10 B) rather than as a fixed
	 * requirement, because the recipe never takes a fixed amount: the machine
	 * drains whatever the tank holds and converts it into a chance.
	 */
	private static final long DISPLAY_FLUID_AMOUNT = FluidValue.BUCKET.multiply(10).getRawValue();

	private static final int INPUT_X = 16;
	private static final int FLUID_X = 16 + 20;
	private static final int OUTPUT_X = FLUID_X + 22 + 2 + 18 + 24;

	public NaquadahDeresonatorEmiRecipe(RecipeEntry<NaquadahDeresonatorRecipe> recipe) {
		super(recipe);
	}

	@Override
	public EmiRecipeCategory getCategory() {
		return TREmiPlugin.NAQUADAH_DERESONATOR_CATEGORY;
	}

	@Override
	public int getDisplayWidth() {
		return OUTPUT_X + 18;
	}

	@Override
	public int getDisplayHeight() {
		return 18 * 4;
	}

	@Override
	public void addWidgets(WidgetHolder widgets) {
		widgets.addSlot(getInput(0), INPUT_X, 18);
		widgets.addSlot(getInput(1), INPUT_X, 18 * 2);
		widgets.add(new TRFluidSlotWidget(FluidVariant.of(recipe.fluid()),
				DISPLAY_FLUID_AMOUNT, FLUID_X, (18 * 4 - 56) / 2,
				NaquadahDeresonatorBlockEntity.TANK_CAPACITY.getRawValue()));

		for (int i = 0; i < 4; i++) {
			widgets.addSlot(getOutput(i), OUTPUT_X, i * 18).recipeContext(this);
		}

		TRUIUtils.energyBar(widgets, recipe, 10, 0, (18 * 4 - 50) / 2);
		TRUIUtils.arrowRight(widgets, recipe, FLUID_X + 22 + 4, (18 * 4 - 10) / 2);
	}
}
