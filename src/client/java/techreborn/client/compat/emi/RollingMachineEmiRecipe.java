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

import org.apache.commons.compress.utils.Lists;
import org.jetbrains.annotations.Nullable;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import techreborn.recipe.recipes.RollingMachineRecipe;

import net.minecraft.util.Identifier;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.ShapedRecipe;

import techreborn.client.compat.emi.core.UIUtils;

public class RollingMachineEmiRecipe implements EmiRecipe {
	private final RollingMachineRecipe recipe;
	private final Identifier id;
	private final List<EmiIngredient> input;
	private final EmiStack output;

	public RollingMachineEmiRecipe(RecipeEntry<RollingMachineRecipe> recipe) {
		this.recipe = recipe.value();
		this.id = recipe.id();
		input = padIngredients(recipe.value().getShapedRecipe());
		output = EmiStack.of(recipe.value().outputs().getFirst());
	}

	@Override
	public EmiRecipeCategory getCategory() {
		return TREmiPlugin.ROLLING_MACHINE_CATEGORY;
	}

	@Override
	public @Nullable Identifier getId() {
		return id;
	}

	@Override
	public List<EmiIngredient> getInputs() {
		return input;
	}

	@Override
	public List<EmiStack> getOutputs() {
		return List.of(output);
	}

	@Override
	public int getDisplayWidth() {
		return 16 + 18 * 3 + 24 + 26;
	}

	@Override
	public int getDisplayHeight() {
		return 18 * 3;
	}

	@Override
	public void addWidgets(WidgetHolder widgets) {
		for (int i = 0; i < 9; ++i) {
			if (i < this.input.size()) {
				widgets.addSlot(this.input.get(i), 16 + i % 3 * 18, i / 3 * 18);
			} else {
				widgets.addSlot(EmiStack.EMPTY, 16 + i % 3 * 18, i / 3 * 18);
			}
		}

		widgets.addSlot(output, 16 + 18 * 3 + 24, (18 * 3 - 26) / 2).large(true).recipeContext(this);

		TRUIUtils.energyBar(widgets, recipe, 10, 0, 2);
		TRUIUtils.arrowRight(widgets, recipe, 16 + 18 * 3 + 4, (18 * 3 - 10) / 2);
		UIUtils.cookTime(widgets, recipe.time(), 16 + 18 * 3 + 2, 0);
	}

	private static List<EmiIngredient> padIngredients(ShapedRecipe recipe) {
		List<EmiIngredient> list = Lists.newArrayList();
		int i = 0;
		for (int y = 0; y < 3; y++) {
			for (int x = 0; x < 3; x++) {
				if (x >= recipe.getWidth() || y >= recipe.getHeight() || i >= recipe.getIngredients().size()) {
					list.add(EmiStack.EMPTY);
				} else {
					list.add(EmiIngredient.of(recipe.getIngredients().get(i++)));
				}
			}
		}
		return list;
	}
}
