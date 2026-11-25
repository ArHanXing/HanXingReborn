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
import reborncore.common.fluid.container.FluidInstance;
import techreborn.client.compat.emi.core.LongHolder;
import techreborn.recipe.recipes.LargeHeatSinkRecipe;

import java.util.List;
import java.util.stream.Stream;

/**
 * The EMI page of the Large Heat Sink.
 * <p>
 * Mirrors the industrial grinder page, because the two machines share their IO
 * shape: coolant tank, item input, arrow, item outputs, product tank. Both tanks
 * are drawn unconditionally - they belong to the machine, not to a single recipe,
 * and EMI indexes the page through {@link #getInputs()}/{@link #getOutputs()}, so
 * the fluid has to be listed there to be searchable.
 */
public class LargeHeatSinkEmiRecipe extends TREmiRecipe<LargeHeatSinkRecipe> {
	private static final int TANK_X = 16;
	private static final int INPUT_X = TANK_X + TRFluidSlotWidget.WIDTH + 2;
	private static final int OUTPUT_X = INPUT_X + 18 + 24;
	private static final int PRODUCT_X = OUTPUT_X + 18 + 2;

	private final List<EmiIngredient> inputsWithFluid;
	private final List<EmiStack> outputsWithFluid;
	private final LongHolder capacityHolder;

	public LargeHeatSinkEmiRecipe(RecipeEntry<LargeHeatSinkRecipe> recipe, LongHolder capacityHolder) {
		super(recipe);
		this.capacityHolder = capacityHolder;

		FluidInstance coolant = recipe.value().fluid();
		long coolantAmount = coolant.getAmount().getRawValue();
		inputsWithFluid = coolant.isEmpty() ? inputs : Stream.concat(inputs.stream(),
			Stream.of(EmiStack.of(coolant.fluid(), coolant.fluidVariant().getComponents(), coolantAmount))).toList();

		FluidInstance product = recipe.value().fluidOutput();
		long productAmount = product.getAmount().getRawValue();
		outputsWithFluid = product.isEmpty() ? outputs : Stream.concat(outputs.stream(),
			Stream.of(EmiStack.of(product.fluid(), product.fluidVariant().getComponents(), productAmount))).toList();

		long needed = Math.max(coolantAmount, productAmount);
		if (needed > capacityHolder.getValue()) {
			capacityHolder.setValue(needed);
		}
	}

	@Override
	public EmiRecipeCategory getCategory() {
		return TREmiPlugin.LARGE_HEAT_SINK_CATEGORY;
	}

	@Override
	public List<EmiIngredient> getInputs() {
		return inputsWithFluid;
	}

	@Override
	public List<EmiStack> getOutputs() {
		return outputsWithFluid;
	}

	@Override
	public int getDisplayWidth() {
		return PRODUCT_X + TRFluidSlotWidget.WIDTH;
	}

	@Override
	public int getDisplayHeight() {
		return 18 * 4;
	}

	@Override
	public void addWidgets(WidgetHolder widgets) {
		widgets.addSlot(getInput(0), INPUT_X, 18 * 3 / 2);

		// Blank variant when the recipe uses no coolant, so the tank renders empty
		// instead of producing an EmiStack out of Fluids.EMPTY.
		FluidInstance coolant = recipe.fluid();
		widgets.add(new TRFluidSlotWidget(coolant.fluidVariant(), coolant.getAmount().getRawValue(),
			TANK_X, (18 * 4 - 56) / 2, capacityHolder.getValue()));

		for (int i = 0; i < 4; i++) {
			widgets.addSlot(getOutput(i), OUTPUT_X, i * 18).recipeContext(this);
		}

		FluidInstance product = recipe.fluidOutput();
		widgets.add(new TRFluidSlotWidget(product.fluidVariant(), product.getAmount().getRawValue(),
			PRODUCT_X, (18 * 4 - 56) / 2, capacityHolder.getValue()));

		TRUIUtils.energyBar(widgets, recipe, 10, 0, (18 * 4 - 50) / 2);
		TRUIUtils.arrowRight(widgets, recipe, INPUT_X + 18 + 4, (18 * 4 - 10) / 2);
	}
}
