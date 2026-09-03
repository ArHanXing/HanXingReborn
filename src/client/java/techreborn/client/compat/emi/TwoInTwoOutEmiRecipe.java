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
 * Generic 2-input / 2-output recipe display, mirroring the machine GUI layout:
 * inputs stacked in a column on the left, outputs in a column on the right,
 * with the energy bar, right arrow and cook time in between. Missing slots are
 * rendered empty so recipes with fewer inputs or outputs still display.
 */
public class TwoInTwoOutEmiRecipe extends TREmiRecipe<RebornRecipe> {
	private final EmiRecipeCategory category;
	private final int machineEnergy;

	public TwoInTwoOutEmiRecipe(RecipeEntry<RebornRecipe> recipe, EmiRecipeCategory category, int machineEnergy) {
		super(recipe);
		this.category = category;
		this.machineEnergy = machineEnergy;
		checkInputCount(2);
		checkOutputCount(2);
	}

	@Override
	public EmiRecipeCategory getCategory() {
		return category;
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
		int largeY = (50 - 26) / 2;

		widgets.addSlot(getInput(0), 16, slotY);
		widgets.addSlot(getInput(1), 16, slotY + 18 + 2);

		widgets.addSlot(getOutput(0), midX + 26, slotY).large(true).recipeContext(this);
		widgets.addSlot(getOutput(1), midX + 26, slotY + 18 + 2).large(true).recipeContext(this);

		TRUIUtils.energyBar(widgets, recipe, machineEnergy, 0, 0);
		TRUIUtils.arrowRight(widgets, recipe, midX + 4, (50 - 10) / 2);
		UIUtils.cookTime(widgets, recipe.time(), 16, 0);
	}
}
