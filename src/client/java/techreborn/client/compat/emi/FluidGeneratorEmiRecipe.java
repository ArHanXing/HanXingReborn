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

import java.util.Collections;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import techreborn.recipe.recipes.FluidGeneratorRecipe;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;

import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.fluid.Fluid;

public class FluidGeneratorEmiRecipe implements EmiRecipe {
	private final FluidGeneratorRecipe recipe;
	private final EmiRecipeCategory category;
	private final Identifier id;
	private final EmiIngredient input;
	private final Fluid fluid;
	private final int fluidCapacity;
	private final int height;

	public FluidGeneratorEmiRecipe(RecipeEntry<FluidGeneratorRecipe> recipe, EmiRecipeCategory category,
								int fluidCapacity, int energyCapacity) {
		this.recipe = recipe.value();
		this.category = category;
		this.id = recipe.id();
		input = EmiStack.of(recipe.value().fluid(), 1000 * 81);
		fluid = recipe.value().fluid();
		this.fluidCapacity = fluidCapacity;
		height = MathHelper.clamp(recipe.value().power() * 48 / energyCapacity, 0, 48);
	}

	@Override
	public EmiRecipeCategory getCategory() {
		return category;
	}

	@Override
	public @Nullable Identifier getId() {
		return id;
	}

	@Override
	public List<EmiIngredient> getInputs() {
		return List.of(input);
	}

	@Override
	public List<EmiStack> getOutputs() {
		return Collections.emptyList();
	}

	@Override
	public int getDisplayWidth() {
		return 22 + 24 + 14;
	}

	@Override
	public int getDisplayHeight() {
		return 56;
	}

	@Override
	public void addWidgets(WidgetHolder widgets) {
		widgets.add(new TRFluidSlotWidget(FluidVariant.of(fluid), 1000 * 81, 0, 0, (long) fluidCapacity * 100 * 81));

		widgets.addTexture(TRTextures.ARROW_RIGHT_EMPTY, 22 + 4, (56 - 10) / 2);
		widgets.addAnimatedTexture(TRTextures.ARROW_RIGHT_FULL, 22 + 4, (56 - 10) / 2, 1000, true, false, false);

		widgets.addTexture(TRTextures.ENERGY_BAR_EMPTY, 22 + 24, 3).tooltip((mx, my) -> List.of(
			TooltipComponent.of(
				Text.translatable("tooltip.techreborn.emi.recipe_power_per_bucket", recipe.power()).asOrderedText())));
		widgets.addTexture(TRTextures.ENERGY_BAR_FULL.texture, 22 + 24 + 1, 4 + 48 - height, 12, height, 141,
			151 + 48 - height);
	}
}
