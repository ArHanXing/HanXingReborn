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
 * Generic 1-input / 2-output recipe display (used by the Large Greenhouse and
 * the Large Ranch). The input slot sits center-left, the two outputs are drawn
 * as large slots on the right, with the energy bar, right arrow and cook time
 * in between. The display is 110x50.
 */
public class SimpleTwoOutputEmiRecipe extends TREmiRecipe<RebornRecipe> {
	private final EmiRecipeCategory category;
	private final int machineEnergy;

	public SimpleTwoOutputEmiRecipe(RecipeEntry<RebornRecipe> recipe, EmiRecipeCategory category, int machineEnergy) {
		super(recipe);
		this.category = category;
		this.machineEnergy = machineEnergy;
		checkInputCount(1);
		checkOutputCount(2);
	}

	@Override
	public EmiRecipeCategory getCategory() {
		return category;
	}

	@Override
	public int getDisplayWidth() {
		return 16 + 18 + 24 + 26 + 26;
	}

	@Override
	public int getDisplayHeight() {
		return 50;
	}

	@Override
	public void addWidgets(WidgetHolder widgets) {
		widgets.addSlot(getInput(0), 16, (50 - 18) / 2);
		widgets.addSlot(getOutput(0), 16 + 18 + 24, (50 - 26) / 2).large(true).recipeContext(this);
		widgets.addSlot(getOutput(1), 16 + 18 + 24 + 26, (50 - 26) / 2).large(true).recipeContext(this);

		TRUIUtils.energyBar(widgets, recipe, machineEnergy, 0, 0);
		TRUIUtils.arrowRight(widgets, recipe, 16 + 18 + 4, (50 - 10) / 2);
		UIUtils.cookTime(widgets, recipe.time(), 16, 0);
	}
}
