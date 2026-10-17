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
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.recipe.RecipeEntry;
import reborncore.common.fluid.FluidValue;
import reborncore.common.fluid.container.FluidInstance;
import techreborn.blockentity.machine.multiblock.VoidDrillBlockEntity;
import techreborn.client.compat.emi.core.UIUtils;
import techreborn.recipe.recipes.VoidDrillRecipe;

import java.util.List;

/**
 * EMI page of the Void Drill: the recipe's display block on the left (renamed to
 * name the biome it stands for) and the fluid it yields on the right.
 */
public class VoidDrillEmiRecipe extends TREmiRecipe<VoidDrillRecipe> {

	private static final long TANK_CAPACITY = VoidDrillBlockEntity.TANK_CAPACITY.getRawValue();

	private final FluidInstance output;
	private final List<EmiStack> fluidOutput;

	public VoidDrillEmiRecipe(RecipeEntry<VoidDrillRecipe> recipe) {
		super(recipe);
		output = new FluidInstance(recipe.value().fluid(), FluidValue.fromMillibuckets(recipe.value().amountMb()));
		fluidOutput = List.of(EmiStack.of(output.fluid(), output.fluidVariant().getComponents(),
				output.getAmount().getRawValue()));
	}

	@Override
	public EmiRecipeCategory getCategory() {
		return TREmiPlugin.VOID_DRILL_CATEGORY;
	}

	@Override
	public List<EmiIngredient> getInputs() {
		return List.of(EmiStack.of(recipe.displayStack()));
	}

	@Override
	public List<EmiStack> getOutputs() {
		return fluidOutput;
	}

	@Override
	public int getDisplayWidth() {
		return 16 + 18 + 24 + 22;
	}

	@Override
	public int getDisplayHeight() {
		return 56;
	}

	@Override
	public void addWidgets(WidgetHolder widgets) {
		widgets.addSlot(EmiStack.of(recipe.displayStack()), 16, (56 - 18) / 2);

		widgets.add(new TRFluidSlotWidget(output, 16 + 18 + 24, 0, TANK_CAPACITY)).recipeContext(this);

		TRUIUtils.energyBar(widgets, recipe, 400, 0, 3);
		TRUIUtils.arrowRight(widgets, recipe, 16 + 18 + 4, (56 - 10) / 2);
		UIUtils.cookTime(widgets, recipe.time(), 16, 0);
	}
}
