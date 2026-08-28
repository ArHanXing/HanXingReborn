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
import net.minecraft.recipe.RecipeEntry;
import reborncore.common.crafting.RebornRecipe;
import techreborn.client.compat.emi.core.UIUtils;

/**
 * EMI recipe for machines with a 4-input (2x2) / 2-output (side by side)
 * layout. Used by the Culture Vat, Irradiation Mutator and Supercritical
 * Polymerizer with their own categories.
 */
public class FourInTwoOutEmiRecipe extends TREmiRecipe<RebornRecipe> {

	private final EmiRecipeCategory category;

	public FourInTwoOutEmiRecipe(RecipeEntry<RebornRecipe> recipe, EmiRecipeCategory category) {
		super(recipe);
		this.category = category;
	}

	@Override
	public EmiRecipeCategory getCategory() {
		return category;
	}

	@Override
	public int getDisplayWidth() {
		return 16 + 18 * 2 + 4 + 24 + 18 * 2 + 4;
	}

	@Override
	public int getDisplayHeight() {
		return 18 * 2 + 4;
	}

	@Override
	public void addWidgets(WidgetHolder widgets) {
		// 4 input slots: 2 columns x 2 rows
		int inputStartX = 16;
		int inputStartY = 2;
		for (int i = 0; i < 4; i++) {
			int col = i % 2;
			int row = i / 2;
			widgets.addSlot(getInput(i), inputStartX + col * 18, inputStartY + row * 18);
		}

		// Arrow
		TRUIUtils.arrowRight(widgets, recipe, inputStartX + 18 * 2 + 4, (18 * 2 + 4 - 10) / 2);
		UIUtils.cookTime(widgets, recipe.time(), inputStartX + 18 * 2 + 2, 0);

		// 2 output slots: side by side, vertically centered
		int outputStartX = inputStartX + 18 * 2 + 4 + 24;
		int outputStartY = (18 * 2 + 4 - 18) / 2;
		widgets.addSlot(getOutput(0), outputStartX, outputStartY).recipeContext(this);
		widgets.addSlot(getOutput(1), outputStartX + 18, outputStartY).recipeContext(this);

		TRUIUtils.energyBar(widgets, recipe, 10, 0, 0);
	}
}
