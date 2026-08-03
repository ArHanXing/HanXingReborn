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

import dev.emi.emi.api.widget.WidgetHolder;
import reborncore.common.crafting.RebornRecipe;

import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.text.Text;

public class TRUIUtils {
	public static void energyBar(WidgetHolder widgets, RebornRecipe recipe, int machineEnergy, int x, int y) {
		widgets.addTexture(TRTextures.ENERGY_BAR_EMPTY, x, y).tooltip((mx, my) -> List.of(
			TooltipComponent.of(
				Text.translatable("tooltip.techreborn.emi.recipe_power", recipe.power()).asOrderedText())));
		widgets.addAnimatedTexture(TRTextures.ENERGY_BAR_FULL, x, y, machineEnergy * 1000 / recipe.power() * 50, false,
			true, true);
	}

	public static void arrowRight(WidgetHolder widgets, RebornRecipe recipe, int x, int y) {
		widgets.addTexture(TRTextures.ARROW_RIGHT_EMPTY, x, y);
		widgets.addAnimatedTexture(TRTextures.ARROW_RIGHT_FULL, x, y, recipe.time() * 50, true, false, false);
	}

	public static void arrowLeft(WidgetHolder widgets, RebornRecipe recipe, int x, int y) {
		widgets.addTexture(TRTextures.ARROW_LEFT_EMPTY, x, y);
		widgets.addAnimatedTexture(TRTextures.ARROW_LEFT_FULL, x, y, recipe.time() * 50, true, true, false);
	}

	public static void arrowUp(WidgetHolder widgets, RebornRecipe recipe, int x, int y) {
		widgets.addTexture(TRTextures.ARROW_UP_EMPTY, x, y);
		widgets.addAnimatedTexture(TRTextures.ARROW_UP_FULL, x, y, recipe.time() * 50, false, true, false);
	}
}
