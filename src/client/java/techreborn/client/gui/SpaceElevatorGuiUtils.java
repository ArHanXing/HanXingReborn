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

package techreborn.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import reborncore.client.gui.GuiBase;

/**
 * Shared drawing helpers for the Space Elevator GUIs: the bound-host info
 * line (units) and scaled-down text (host module list).
 */
public final class SpaceElevatorGuiUtils {

	private SpaceElevatorGuiUtils() {
	}

	/**
	 * Draws the binding state of a unit machine: the bound host coordinates
	 * or an unbound hint.
	 *
	 * @param gui      {@link GuiBase} the gui to draw on
	 * @param hostPos  {@link BlockPos} the bound host position, or {@code null}
	 * @param x        {@code int} gui-local x
	 * @param y        {@code int} gui-local y
	 * @param layer    {@link GuiBase.Layer} the layer to draw on
	 */
	public static void drawBoundInfo(GuiBase<?> gui, DrawContext drawContext, @Nullable BlockPos hostPos, int x, int y,
			GuiBase.Layer layer) {
		if (hostPos == null) {
			gui.drawText(drawContext, Text.translatable("gui.techreborn.space_elevator.unbound").formatted(Formatting.GRAY),
					x, y, 0x404040, layer);
		} else {
			gui.drawText(drawContext, Text.translatable("gui.techreborn.space_elevator.bound_to",
					Text.literal(String.valueOf(hostPos.getX())).formatted(Formatting.YELLOW),
					Text.literal(String.valueOf(hostPos.getY())).formatted(Formatting.YELLOW),
					Text.literal(String.valueOf(hostPos.getZ())).formatted(Formatting.YELLOW)),
					x, y, 0x404040, layer);
		}
	}

	/**
	 * Draws text scaled down (anchored at the gui origin), used to fit the
	 * host's module coordinate list without overlapping the energy bar.
	 *
	 * @param gui   {@link GuiBase} the gui to draw on
	 * @param text  {@link Text} the text to draw
	 * @param x     {@code int} gui-local x
	 * @param y     {@code int} gui-local y
	 * @param scale {@code float} scale factor (e.g. 0.7)
	 * @param color {@code int} text color
	 */
	public static void drawScaledText(GuiBase<?> gui, DrawContext drawContext, Text text, int x, int y, float scale,
			int color, GuiBase.Layer layer) {
		drawContext.getMatrices().push();
		drawContext.getMatrices().scale(scale, scale, 1.0F);
		drawContext.drawText(MinecraftClient.getInstance().textRenderer, text, Math.round(x / scale), Math.round(y / scale),
				color, false);
		drawContext.getMatrices().pop();
	}
}
