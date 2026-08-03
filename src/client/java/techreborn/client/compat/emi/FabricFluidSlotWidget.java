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

import org.jetbrains.annotations.Nullable;

import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.SlotWidget;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.base.ResourceAmount;

public class FabricFluidSlotWidget extends SlotWidget {
	protected final float fluidFullness;
	protected final @Nullable FluidVariant fluid;

	public FabricFluidSlotWidget(@Nullable FluidVariant fluid, long amount, int x, int y, long capacity) {
		super(fluid == null ? EmiStack.EMPTY : EmiStack.of(fluid.getFluid(), fluid.getComponents(), amount), x, y);
		fluidFullness = Math.min((float) ((double) amount / (double) capacity), 1.0f);
		this.fluid = fluid;
	}

	public FabricFluidSlotWidget(@Nullable ResourceAmount<FluidVariant> res, int x, int y, long capacity) {
		this(res == null ? null : res.resource(), res == null ? 0 : res.amount(), x, y, capacity);
	}
}
