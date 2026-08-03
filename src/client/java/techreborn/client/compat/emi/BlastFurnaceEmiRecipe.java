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
import techreborn.recipe.recipes.BlastFurnaceRecipe;

import net.minecraft.text.Text;
import net.minecraft.recipe.RecipeEntry;

import techreborn.client.compat.emi.core.EmiTextures;
import techreborn.client.compat.emi.core.NinePatchWidget;
import techreborn.client.compat.emi.core.UIUtils;

public class BlastFurnaceEmiRecipe extends TREmiRecipe<BlastFurnaceRecipe> {
	public BlastFurnaceEmiRecipe(RecipeEntry<BlastFurnaceRecipe> recipe) {
		super(recipe);
	}

	@Override
	public EmiRecipeCategory getCategory() {
		return TREmiPlugin.BLAST_FURNACE_CATEGORY;
	}

	@Override
	public int getDisplayWidth() {
		return 16 + 18 + 24 + 4 + 18 + 2 + 18 + 4;
	}

	@Override
	public int getDisplayHeight() {
		return 50;
	}

	@Override
	public void addWidgets(WidgetHolder widgets) {
		widgets.addSlot(getInput(0), 16, (50 - 18 * 2 - 2) / 2);
		widgets.addSlot(getInput(1), 16, (50 - 18 * 2 - 2) / 2 + 18 + 2);

		widgets.add(new NinePatchWidget(EmiTextures.SLOT_BG, 16 + 18 + 24, (50 - 26) / 2, 4 + 18 + 2 + 18 + 4, 26));
		widgets.addSlot(getOutput(0), 16 + 18 + 24 + 4, (50 - 18) / 2).drawBack(false).recipeContext(this);
		widgets.addSlot(getOutput(1), 16 + 18 + 24 + 4 + 18 + 2, (50 - 18) / 2).drawBack(false).recipeContext(this);

		TRUIUtils.energyBar(widgets, recipe, 40, 0, 0);
		TRUIUtils.arrowRight(widgets, recipe, 16 + 18 + 4, (50 - 10) / 2);
		UIUtils.cookTime(widgets, recipe.time(), 16 + 18 + 2, 0);
		widgets.addText(Text.translatable("gui.techreborn.emi.heat", recipe.getHeat()).asOrderedText(),
			16 + 18 + 2, 50 - 9, 0xFF3F3F3F, false);
	}
}
