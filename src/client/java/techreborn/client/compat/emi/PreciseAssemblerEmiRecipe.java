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

import techreborn.client.compat.emi.core.UIUtils;

/**
 * EMI recipe for the Precise Assembler: up to 16 inputs in a 4x4 grid and up
 * to 4 outputs in a 2x2 grid.
 */
public class PreciseAssemblerEmiRecipe extends TREmiRecipe<RebornRecipe> {
	public PreciseAssemblerEmiRecipe(RecipeEntry<RebornRecipe> recipe) {
		super(recipe);
	}

	@Override
	public EmiRecipeCategory getCategory() {
		return TREmiPlugin.PRECISE_ASSEMBLER_CATEGORY;
	}

	@Override
	public int getDisplayWidth() {
		return 12 + 18 * 4 + 2 + 24 + 4 + 18 * 2 + 2 + 4;
	}

	@Override
	public int getDisplayHeight() {
		return 6 + 18 * 4 + 6;
	}

	@Override
	public void addWidgets(WidgetHolder widgets) {
		// 16 inputs: 4 columns x 4 rows
		int inX = 12;
		int inY = 6;
		for (int row = 0; row < 4; row++) {
			for (int col = 0; col < 4; col++) {
				widgets.addSlot(getInput(row * 4 + col), inX + col * 18, inY + row * 18);
			}
		}

		// 4 outputs: 2 columns x 2 rows
		int outX = inX + 18 * 4 + 2 + 24 + 4;
		widgets.addSlot(getOutput(0), outX, inY).recipeContext(this);
		widgets.addSlot(getOutput(1), outX + 18 + 2, inY).recipeContext(this);
		widgets.addSlot(getOutput(2), outX, inY + 18 + 2).recipeContext(this);
		widgets.addSlot(getOutput(3), outX + 18 + 2, inY + 18 + 2).recipeContext(this);

		TRUIUtils.energyBar(widgets, recipe, 10, 0, 0);
		TRUIUtils.arrowRight(widgets, recipe, inX + 18 * 4 + 2, (getDisplayHeight() - 10) / 2);
		UIUtils.cookTime(widgets, recipe.time(), outX, 0);
	}
}
