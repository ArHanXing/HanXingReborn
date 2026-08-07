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
 * Recipe for pouring a fluid out of a filled container:
 * {@code filled container -> fluid + empty container}.
 * <p>
 * Layout: the filled container is on the left, an arrow points to the fluid
 * and the empty container on the right.
 */
public class FluidFromContainerEmiRecipe implements EmiRecipe {
	private final Identifier id;
	private final EmiStack output;
	private final EmiIngredient input;
	private final EmiStack container;

	public FluidFromContainerEmiRecipe(Identifier id, EmiStack output, EmiStack input, EmiStack container) {
		this.id = id;
		this.output = output;
		this.input = input;
		this.container = container;
	}

	@Override
	public EmiRecipeCategory getCategory() {
		return TREmiPlugin.FLUID_FROM_CONTAINER_CATEGORY;
	}

	@Override
	public @Nullable Identifier getId() {
		return id;
	}

	@Override
	public List<EmiIngredient> getInputs() {
		return List.of(input);
	}

	@Override
	public List<EmiStack> getOutputs() {
		return List.of(output, container);
	}

	@Override
	public int getDisplayWidth() {
		return 18 + 24 + 18 + 18;
	}

	@Override
	public int getDisplayHeight() {
		return 38;
	}

	@Override
	public void addWidgets(WidgetHolder widgets) {
		// Input: filled container
		widgets.addSlot(input, 0, 10);
		// Arrow from input to outputs
		widgets.addTexture(TRTextures.ARROW_RIGHT_EMPTY, 24, 14);
		// Outputs: fluid and empty container
		widgets.addSlot(output, 48, 0).recipeContext(this);
		widgets.addSlot(container, 68, 0).recipeContext(this);
	}
}
