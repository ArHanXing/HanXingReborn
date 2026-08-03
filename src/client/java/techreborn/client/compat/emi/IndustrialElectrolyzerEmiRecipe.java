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

import techreborn.client.compat.emi.core.EmiTextures;
import techreborn.client.compat.emi.core.NinePatchWidget;
import techreborn.client.compat.emi.core.UIUtils;

public class IndustrialElectrolyzerEmiRecipe extends TREmiRecipe<RebornRecipe> {
	public IndustrialElectrolyzerEmiRecipe(RecipeEntry<RebornRecipe> recipe) {
		super(recipe);
	}

	@Override
	public EmiRecipeCategory getCategory() {
		return TREmiPlugin.INDUSTRIAL_ELECTROLYZER_CATEGORY;
	}

	@Override
	public int getDisplayWidth() {
		return 16 + 18 * 5 + 2 * 2;
	}

	@Override
	public int getDisplayHeight() {
		return 50;
	}

	@Override
	public void addWidgets(WidgetHolder widgets) {
		widgets.addSlot(getInput(0), 16, 1 + 26 + 4);
		widgets.addSlot(getInput(1), 16 + 18 + 2, 1 + 26 + 4);

		int outputWidth = 18 * 4 + 2 * 3 + 4 * 2;
		int outputX = 16 + (18 * 5 + 2 * 2 - outputWidth) / 2;
		widgets.add(new NinePatchWidget(EmiTextures.SLOT_BG, outputX, 1, outputWidth, 26));
		widgets.addSlot(getOutput(0), outputX + 4, 1 + 4).drawBack(false).recipeContext(this);
		widgets.addSlot(getOutput(1), outputX + 4 + 18 + 2, 1 + 4).drawBack(false).recipeContext(this);
		widgets.addSlot(getOutput(2), outputX + 4 + 18 * 2 + 2 * 2, 1 + 4).drawBack(false).recipeContext(this);
		widgets.addSlot(getOutput(3), outputX + 4 + 18 * 3 + 2 * 3, 1 + 4).drawBack(false).recipeContext(this);

		TRUIUtils.energyBar(widgets, recipe, 10, 0, 0);
		TRUIUtils.arrowUp(widgets, recipe, 16 + 18 * 2 + 2 + 4, 1 + 26 + 4 + 1);
		UIUtils.cookTime(widgets, recipe.time(), 16 + 18 * 3 + 2, 50 - 9);
	}
}
