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

import java.util.List;

import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.widget.WidgetHolder;
import techreborn.recipe.recipes.FusionReactorRecipe;

import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.text.Text;
import net.minecraft.recipe.RecipeEntry;

import techreborn.client.compat.emi.core.UIUtils;

public class FusionReactorEmiRecipe extends TREmiRecipe<FusionReactorRecipe> {
	public FusionReactorEmiRecipe(RecipeEntry<FusionReactorRecipe> recipe) {
		super(recipe);
	}

	@Override
	public EmiRecipeCategory getCategory() {
		return TREmiPlugin.FUSION_REACTOR_CATEGORY;
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
		widgets.addSlot(getInput(0), 16, (50 - 18) / 2);
		widgets.addSlot(getOutput(0), 16 + 18 + 24, (50 - 26) / 2).large(true).recipeContext(this);
		widgets.addSlot(getInput(1), 16 + 18 + 24 + 26 + 24, (50 - 18) / 2);

		int power = recipe.power();
		int displayedPower;
		String tooltip;
		if (power < 0) {
			displayedPower = -power;
			tooltip = "recipe_power.consumed";
		} else {
			displayedPower = power;
			tooltip = "recipe_power.produced";
		}

		widgets.addTexture(TRTextures.ENERGY_BAR_EMPTY, 0, 0).tooltip((mx, my) -> List.of(
			TooltipComponent.of(
				Text.translatable("tooltip.techreborn.emi." + tooltip, displayedPower).asOrderedText())));
		widgets.addAnimatedTexture(TRTextures.ENERGY_BAR_FULL, 0, 0, 100000 * 1000 / displayedPower * 50, false, true,
			power < 0);

		TRUIUtils.arrowRight(widgets, recipe, 16 + 18 + 4, (50 - 10) / 2);
		TRUIUtils.arrowLeft(widgets, recipe, 16 + 18 + 24 + 26 + 4, (50 - 10) / 2);
		UIUtils.cookTime(widgets, recipe.time(), 16, 0);
		widgets.addText(Text.translatable("gui.techreborn.emi.start_e",
			UIUtils.metricNumber(recipe.getStartEnergy())).asOrderedText(), 16, 50 - 9, 0xFF3F3F3F, false);
		widgets.addText(Text.translatable("gui.techreborn.emi.min_size", recipe.getMinSize()).asOrderedText(),
			16 + 18 + 24 + 13, 0, 0xFF3F3F3F, false);
	}
}
