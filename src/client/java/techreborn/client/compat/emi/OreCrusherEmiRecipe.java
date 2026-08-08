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

import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.widget.WidgetHolder;
import reborncore.common.crafting.RebornRecipe;

import net.minecraft.recipe.RecipeEntry;

/**
 * Ore Crusher recipe display. Uses the same layout as the Industrial Grinder
 * (thumbnail style): the input slot sits center-left, the 16 stone output is
 * drawn as a large slot on the right, with the energy bar and right arrow in
 * between. The display is 100x72.
 */
public class OreCrusherEmiRecipe extends TREmiRecipe<RebornRecipe> {

	public OreCrusherEmiRecipe(RecipeEntry<RebornRecipe> recipe) {
		super(recipe);
		checkInputCount(1);
		checkOutputCount(1);
	}

	@Override
	public EmiRecipeCategory getCategory() {
		return TREmiPlugin.ORE_CRUSHER_CATEGORY;
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
		widgets.addSlot(getInput(0), 16 + 22 + 2, 18 * 3 / 2).recipeContext(this);
		widgets.addSlot(getOutput(0), 16 + 22 + 2 + 18 + 24, (18 * 4 - 26) / 2).large(true).recipeContext(this);

		TRUIUtils.energyBar(widgets, recipe, 10, 0, (18 * 4 - 50) / 2);
		TRUIUtils.arrowRight(widgets, recipe, 16 + 22 + 2 + 18 + 4, (18 * 4 - 10) / 2);
	}
}
