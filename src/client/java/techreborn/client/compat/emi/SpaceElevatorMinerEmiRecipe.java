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
 * EMI recipe for the Space Elevator Miner Unit: same layout as the
 * Distillation Tower (4 inputs, 6 outputs, each output slot with its own
 * background) but with its own recipe category.
 */
public class SpaceElevatorMinerEmiRecipe extends TREmiRecipe<RebornRecipe> {
	public SpaceElevatorMinerEmiRecipe(RecipeEntry<RebornRecipe> recipe) {
		super(recipe);
	}

	@Override
	public EmiRecipeCategory getCategory() {
		return TREmiPlugin.SPACE_ELEVATOR_MINER_CATEGORY;
	}

	@Override
	public int getDisplayWidth() {
		return 16 + 18 * 2 + 24 + 4 + 18 * 3 + 2 * 2 + 4;
	}

	@Override
	public int getDisplayHeight() {
		return 50;
	}

	@Override
	public void addWidgets(WidgetHolder widgets) {
		// 4 inputs: 2 columns x 2 rows
		int inX = 16;
		int inY = (50 - 18 * 2 - 2) / 2;
		widgets.addSlot(getInput(0), inX, inY);
		widgets.addSlot(getInput(1), inX + 18, inY);
		widgets.addSlot(getInput(2), inX, inY + 18 + 2);
		widgets.addSlot(getInput(3), inX + 18, inY + 18 + 2);

		// 6 outputs: 3 columns x 2 rows, each slot drawn with its own background
		int outX = inX + 18 * 2 + 24 + 4;
		for (int i = 0; i < 6; i++) {
			int col = i % 3;
			int row = i / 3;
			widgets.addSlot(getOutput(i), outX + col * (18 + 2), inY + row * (18 + 2)).recipeContext(this);
		}

		TRUIUtils.energyBar(widgets, recipe, 10, 0, 0);
		TRUIUtils.arrowRight(widgets, recipe, inX + 18 * 2 + 4, (50 - 10) / 2);
		UIUtils.cookTime(widgets, recipe.time(), inX + 18 * 2 + 2, 0);
	}
}
