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

import org.jetbrains.annotations.Nullable;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import reborncore.common.crafting.RebornRecipe;

import net.minecraft.recipe.RecipeEntry;
import net.minecraft.util.Identifier;

import techreborn.client.compat.emi.core.ExMILog;

public abstract class TREmiRecipe<R extends RebornRecipe> implements EmiRecipe {
	protected final R recipe;
	protected final @Nullable Identifier id;
	protected final List<EmiIngredient> inputs;
	protected final List<EmiStack> outputs;

	public TREmiRecipe(RecipeEntry<? extends R> recipe) {
		this.recipe = recipe.value();
		id = recipe.id();
		inputs = recipe.value().ingredients().stream().map(ing -> EmiIngredient.of(ing.ingredient(), ing.count())).toList();
		outputs = recipe.value().outputs().stream().map(EmiStack::of).toList();
	}

	@Override
	public @Nullable Identifier getId() {
		return id;
	}

	@Override
	public List<EmiIngredient> getInputs() {
		return inputs;
	}

	@Override
	public List<EmiStack> getOutputs() {
		return outputs;
	}

	protected final void checkInputCount(int count) {
		if (inputs.size() != count) {
			ExMILog.LOG.warn("[ExMI Tech Reborn] Expected recipe {} ({}) to have {} inputs but instead it has {}", id,
				recipe, count, inputs.size());
		}
	}

	protected final void checkOutputCount(int count) {
		if (outputs.size() != count) {
			ExMILog.LOG.warn("[ExMI Tech Reborn] Expected recipe {} ({}) to have {} outputs but instead it has {}", id,
				recipe, count, outputs.size());
		}
	}

	protected EmiIngredient getInput(int index) {
		if (index >= inputs.size()) {
			return EmiStack.EMPTY;
		} else {
			return inputs.get(index);
		}
	}

	protected EmiStack getOutput(int index) {
		if (index >= outputs.size()) {
			return EmiStack.EMPTY;
		} else {
			return outputs.get(index);
		}
	}
}
