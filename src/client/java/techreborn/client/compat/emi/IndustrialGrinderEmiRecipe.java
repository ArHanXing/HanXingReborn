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
import java.util.stream.Stream;

import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import reborncore.common.fluid.container.FluidInstance;
import techreborn.recipe.recipes.IndustrialGrinderRecipe;

import net.minecraft.recipe.RecipeEntry;

import techreborn.client.compat.emi.core.LongHolder;

public class IndustrialGrinderEmiRecipe extends TREmiRecipe<IndustrialGrinderRecipe> {
	private final List<EmiIngredient> inputsWithFluids;
	private final LongHolder capacityHolder;

	public IndustrialGrinderEmiRecipe(RecipeEntry<IndustrialGrinderRecipe> recipe, LongHolder capacityHolder) {
		super(recipe);
		this.capacityHolder = capacityHolder;
		FluidInstance instance = recipe.value().fluid();
		long amount = instance.getAmount().getRawValue();
		inputsWithFluids = Stream.concat(inputs.stream(),
			Stream.of(EmiStack.of(instance.fluid(), instance.fluidVariant().getComponents(), amount))).toList();

		if (amount > capacityHolder.getValue()) {
			capacityHolder.setValue(amount);
		}
	}

	@Override
	public EmiRecipeCategory getCategory() {
		return TREmiPlugin.INDUSTRIAL_GRINDER_CATEGORY;
	}

	@Override
	public List<EmiIngredient> getInputs() {
		return inputsWithFluids;
	}

	@Override
	public int getDisplayWidth() {
		return 16 + 22 + 2 + 18 + 24 + 18;
	}

	@Override
	public int getDisplayHeight() {
		return 18 * 4;
	}

	@Override
	public void addWidgets(WidgetHolder widgets) {
		widgets.addSlot(getInput(0), 16 + 22 + 2, 18 * 3 / 2);
		widgets.add(new TRFluidSlotWidget(recipe.fluid(), 16, (18 * 4 - 56) / 2, capacityHolder.getValue()));

		for (int i = 0; i < 4; i++) {
			widgets.addSlot(getOutput(i), 16 + 22 + 2 + 18 + 24, i * 18).recipeContext(this);
		}

		TRUIUtils.energyBar(widgets, recipe, 10, 0, (18 * 4 - 50) / 2);
		TRUIUtils.arrowRight(widgets, recipe, 16 + 22 + 2 + 18 + 4, (18 * 4 - 10) / 2);
	}
}
