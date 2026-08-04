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

package techreborn.recipe;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeType;
import reborncore.common.crafting.RebornRecipe;
import reborncore.common.crafting.SizedIngredient;

import java.util.List;

/**
 * Wraps a recipe coming from another (smaller) machine so it can be run inside
 * a proxy (larger) machine with adjusted time and power values.
 * <p>
 * Every other behaviour (ingredients, outputs, crafting hooks) is delegated to
 * the wrapped recipe, so the machine treats it exactly like its own recipe.
 */
public record ProxyRecipe(RebornRecipe delegate, float timeMultiplier, float powerMultiplier) implements RebornRecipe {

	@Override
	public RecipeType<?> type() {
		return delegate.type();
	}

	@Override
	public List<SizedIngredient> ingredients() {
		return delegate.ingredients();
	}

	@Override
	public List<ItemStack> outputs() {
		return delegate.outputs();
	}

	@Override
	public int power() {
		return Math.max((int) (delegate.power() * powerMultiplier), 1);
	}

	@Override
	public int time() {
		return Math.max((int) (delegate.time() * timeMultiplier), 1);
	}

	@Override
	public boolean canCraft(BlockEntity blockEntity) {
		return delegate.canCraft(blockEntity);
	}

	@Override
	public boolean onCraft(BlockEntity blockEntity) {
		return delegate.onCraft(blockEntity);
	}
}
