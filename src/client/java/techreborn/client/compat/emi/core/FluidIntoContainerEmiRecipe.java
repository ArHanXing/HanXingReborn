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

package techreborn.client.compat.emi.core;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;

import net.minecraft.util.Identifier;

import techreborn.client.compat.emi.TREmiPlugin;
import techreborn.client.compat.emi.TRTextures;

/**
 * Recipe for pouring a fluid into an empty container:
 * {@code fluid + empty container -> filled container}.
 * <p>
 * Layout: the two inputs (fluid, empty container) are stacked on the left, an
 * arrow points to the filled container on the right.
 */
public class FluidIntoContainerEmiRecipe implements EmiRecipe {
	private final Identifier id;
	private final EmiStack input;
	private final EmiIngredient containerInput;
	private final EmiStack container;

	public FluidIntoContainerEmiRecipe(Identifier id, EmiStack input, EmiStack containerInput, EmiStack container) {
		this.id = id;
		this.input = input;
		this.containerInput = containerInput;
		this.container = container;
	}

	@Override
	public EmiRecipeCategory getCategory() {
		return TREmiPlugin.FLUID_INTO_CONTAINER_CATEGORY;
	}

	@Override
	public @Nullable Identifier getId() {
		return id;
	}

	@Override
	public List<EmiIngredient> getInputs() {
		return List.of(input, containerInput);
	}

	@Override
	public List<EmiStack> getOutputs() {
		return List.of(container);
	}

	@Override
	public int getDisplayWidth() {
		return 18 + 24 + 18;
	}

	@Override
	public int getDisplayHeight() {
		return 38;
	}

	@Override
	public void addWidgets(WidgetHolder widgets) {
		// Inputs: fluid (top) and empty container (bottom)
		widgets.addSlot(input, 0, 0);
		widgets.addSlot(containerInput, 0, 20);
		// Arrow from inputs to output
		widgets.addTexture(TRTextures.ARROW_RIGHT_EMPTY, 24, 14);
		// Output: filled container
		widgets.addSlot(container, 48, 10).recipeContext(this);
	}
}
