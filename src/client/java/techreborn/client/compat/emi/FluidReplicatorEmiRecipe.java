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

package techreborn.client.compat.emi;

import java.util.List;

import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import reborncore.common.fluid.container.FluidInstance;
import techreborn.recipe.recipes.FluidReplicatorRecipe;

import net.minecraft.recipe.RecipeEntry;

import techreborn.client.compat.emi.core.LongHolder;
import techreborn.client.compat.emi.core.UIUtils;

public class FluidReplicatorEmiRecipe extends TREmiRecipe<FluidReplicatorRecipe> {
	private final List<EmiStack> fluidOutput;
	private final LongHolder capacityHolder;

	public FluidReplicatorEmiRecipe(RecipeEntry<FluidReplicatorRecipe> recipe, LongHolder capacityHolder) {
		super(recipe);
		this.capacityHolder = capacityHolder;
		FluidInstance instance = recipe.value().fluid();
		long amount = instance.getAmount().getRawValue();
		fluidOutput = List.of(EmiStack.of(instance.fluid(), instance.fluidVariant().getComponents(), amount));

		if (amount > capacityHolder.getValue()) {
			capacityHolder.setValue(amount);
		}
	}

	@Override
	public EmiRecipeCategory getCategory() {
		return TREmiPlugin.FLUID_REPLICATOR_CATEGORY;
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
		widgets.addSlot(getInput(0), 16, (56 - 18) / 2);

		widgets.add(new TRFluidSlotWidget(recipe.fluid(), 16 + 18 + 24, 0, capacityHolder.getValue()))
			.recipeContext(this);

		TRUIUtils.energyBar(widgets, recipe, 400, 0, 3);
		TRUIUtils.arrowRight(widgets, recipe, 16 + 18 + 4, (56 - 10) / 2);
		UIUtils.cookTime(widgets, recipe.time(), 16, 0);
	}
}
