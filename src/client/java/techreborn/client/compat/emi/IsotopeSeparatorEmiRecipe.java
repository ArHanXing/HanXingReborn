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

import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.recipe.RecipeEntry;

import techreborn.client.compat.emi.core.UIUtils;
import techreborn.recipe.recipes.IsotopeSeparatorRecipe;

/**
 * EMI display for the Isotope Separator: feed and rotor on the left, product and
 * tails on the right. Uses the machine-specific recipe type so the optional
 * {@code degraded_output} is available to the display.
 */
public class IsotopeSeparatorEmiRecipe extends TREmiRecipe<IsotopeSeparatorRecipe> {

	public IsotopeSeparatorEmiRecipe(RecipeEntry<IsotopeSeparatorRecipe> recipe) {
		super(recipe);
		checkInputCount(2);
		checkOutputCount(2);
	}

	@Override
	public dev.emi.emi.api.recipe.EmiRecipeCategory getCategory() {
		return TREmiPlugin.ISOTOPE_SEPARATOR_CATEGORY;
	}

	@Override
	public int getDisplayWidth() {
		return 16 + 18 + 24 + 26 + 24 + 18;
	}

	@Override
	public int getDisplayHeight() {
		return 50;
	}

	@Override
	public void addWidgets(WidgetHolder widgets) {
		int midX = 16 + 18 + 24;
		int slotY = (50 - 18 * 2 - 2) / 2;

		widgets.addSlot(getInput(0), 16, slotY);
		widgets.addSlot(getInput(1), 16, slotY + 18 + 2);

		widgets.addSlot(getOutput(0), midX + 26, slotY).recipeContext(this);
		widgets.addSlot(getOutput(1), midX + 26, slotY + 18 + 2).recipeContext(this);

		TRUIUtils.energyBar(widgets, recipe, 10, 0, 0);
		TRUIUtils.arrowRight(widgets, recipe, midX + 4, (50 - 10) / 2);
		UIUtils.cookTime(widgets, recipe.time(), 16, 0);
	}
}
