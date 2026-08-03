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

public class LargeChemicalReactorEmiRecipe extends TREmiRecipe<RebornRecipe> {
	public LargeChemicalReactorEmiRecipe(RecipeEntry<RebornRecipe> recipe) {
		super(recipe);
	}

	@Override
	public EmiRecipeCategory getCategory() {
		return TREmiPlugin.LARGE_CHEMICAL_REACTOR_CATEGORY;
	}

	@Override
	public int getDisplayWidth() {
		return 16 + 18 * 2 + 4 + 24 + 18 * 2 + 4;
	}

	@Override
	public int getDisplayHeight() {
		return 18 * 3 + 4;
	}

	@Override
	public void addWidgets(WidgetHolder widgets) {
		// 6 input slots: 2 columns × 3 rows
		int inputStartX = 16;
		int inputStartY = 2;
		int[] inputOrder = {0, 3, 1, 4, 2, 5};
		for (int idx = 0; idx < 6; idx++) {
			int col = idx % 2;
			int row = idx / 2;
			widgets.addSlot(getInput(inputOrder[idx]), inputStartX + col * 18, inputStartY + row * 18);
		}

		// Arrow
		TRUIUtils.arrowRight(widgets, recipe, inputStartX + 18 * 2 + 4, (18 * 3 + 4 - 10) / 2);

		// 4 output slots: 2 columns × 2 rows
		int outputStartX = inputStartX + 18 * 2 + 4 + 24;
		int outputStartY = (18 * 3 + 4 - 18 * 2) / 2;
		for (int i = 0; i < 4; i++) {
			int col = i % 2;
			int row = i / 2;
			widgets.addSlot(getOutput(i), outputStartX + col * 18, outputStartY + row * 18).recipeContext(this);
		}

		TRUIUtils.energyBar(widgets, recipe, 256, 0, 0);
	}
}
