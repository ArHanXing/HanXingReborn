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

import dev.emi.emi.api.render.EmiRender;
import dev.emi.emi.api.widget.Bounds;
import reborncore.common.fluid.container.FluidInstance;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;

import net.minecraft.client.gui.DrawContext;

import techreborn.client.compat.emi.core.UIUtils;

public class TRFluidSlotWidget extends FabricFluidSlotWidget {
	public static final int WIDTH = 22;
	public static final int HEIGHT = 56;
	public static final float FLUID_AREA_WIDTH = 14f;
	public static final float FLUID_AREA_HEIGHT = 48f;

	public TRFluidSlotWidget(FluidInstance fluid, int x, int y, long capacity) {
		super(fluid.fluidVariant(), fluid.getAmount().getRawValue(), x, y, capacity);
	}

	public TRFluidSlotWidget(FluidVariant fluid, long amount, int x, int y, long capacity) {
		super(fluid, amount, x, y, capacity);
	}

	@Override
	public Bounds getBounds() {
		return new Bounds(x, y, WIDTH, HEIGHT);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		if (drawBack) {
			TRTextures.TANK_BASE.render(context, x, y, delta);
		}

		if (fluid != null) {
			FabricUIUtils.renderFluid(context.getMatrices(), fluid, x + 4, y + 4, FLUID_AREA_HEIGHT,
				fluidFullness * FLUID_AREA_HEIGHT, FLUID_AREA_WIDTH);
		}

		if (drawBack) {
			context.getMatrices().push();
			context.getMatrices().translate(0.0F, 0.0F, 50.0F);
			TRTextures.TANK_GRADUATION.render(context, x + 3, y + 3, delta);
			context.getMatrices().pop();
		}

		if (this.catalyst) {
			EmiRender.renderCatalystIcon(this.getStack(), context, x + 2, y + 4);
		}

		Bounds bounds = getBounds();
		if (bounds.contains(mouseX, mouseY)) {
			UIUtils.drawSlotHightlight(context, bounds.x() + 4, bounds.y() + 4, bounds.width() - 8,
				bounds.height() - 8);
		}
	}
}
