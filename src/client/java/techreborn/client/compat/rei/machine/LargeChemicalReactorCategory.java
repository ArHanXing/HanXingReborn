/*
 * This file is part of TechReborn, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2020 TechReborn
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

package techreborn.client.compat.rei.machine;

import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import net.minecraft.recipe.RecipeType;
import net.minecraft.text.Text;
import reborncore.client.gui.GuiBuilder;
import reborncore.common.crafting.RebornRecipe;
import techreborn.client.compat.rei.MachineRecipeDisplay;
import techreborn.client.compat.rei.ReiPlugin;

import java.text.DecimalFormat;
import java.util.List;

public class LargeChemicalReactorCategory<R extends RebornRecipe> extends AbstractEnergyConsumingMachineCategory<R> {
	public LargeChemicalReactorCategory(RecipeType<R> rebornRecipeType) {
		super(rebornRecipeType);
	}

	@Override
	public int getDisplayWidth(MachineRecipeDisplay<R> display) {
		return 138;
	}

	@Override
	public int getDisplayHeight() {
		return 70;
	}

	@Override
	public List<Widget> setupDisplay(MachineRecipeDisplay<R> recipeDisplay, Rectangle bounds) {
		List<Widget> widgets = super.setupDisplay(recipeDisplay, bounds);

		// 6 input slots: 2 columns × 3 rows
		int inputStartX = bounds.x + 30;
		int inputStartY = bounds.y + 8;
		int[] inputIndices = {0, 3, 1, 4, 2, 5};
		for (int i = 0; i < 6; i++) {
			int col = i % 2;
			int row = i / 2;
			widgets.add(Widgets.createSlot(new Point(inputStartX + col * 20, inputStartY + row * 20))
				.entries(getInput(recipeDisplay, inputIndices[i])).markInput());
		}

		// Arrow
		widgets.add(ReiPlugin.createProgressBar(bounds.x + 72, bounds.y + 30, recipeDisplay.getTime() * 50, GuiBuilder.ProgressDirection.RIGHT));

		// 4 output slots: 2 columns × 2 rows
		int outputStartX = bounds.x + 92;
		int outputStartY = bounds.y + 17;
		for (int i = 0; i < 4; i++) {
			int col = i % 2;
			int row = i / 2;
			widgets.add(Widgets.createSlot(new Point(outputStartX + col * 20, outputStartY + row * 20))
				.entries(getOutput(recipeDisplay, i)).markOutput());
		}

		widgets.add(Widgets.createLabel(new Point(bounds.getMaxX() - 5, bounds.y + 5),
			Text.translatable("techreborn.jei.recipe.processing.time.3",
				new DecimalFormat("###.##").format(recipeDisplay.getTime() / 20.0)))
			.shadow(false)
			.rightAligned()
			.color(0xFF404040, 0xFFBBBBBB)
		);

		return widgets;
	}
}
