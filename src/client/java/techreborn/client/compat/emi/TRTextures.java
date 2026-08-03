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

import dev.emi.emi.api.render.EmiTexture;

import net.minecraft.util.Identifier;

public class TRTextures {
	public static final Identifier REBORNCORE_GUI_ELEMENTS =
		Identifier.of("reborncore", "textures/gui/guielements.png");

	public static final EmiTexture ENERGY_BAR_EMPTY = new EmiTexture(REBORNCORE_GUI_ELEMENTS, 126, 150, 14, 50);
	public static final EmiTexture ENERGY_BAR_FULL = new EmiTexture(REBORNCORE_GUI_ELEMENTS, 140, 150, 14, 50);

	public static final EmiTexture TANK_BASE = new EmiTexture(REBORNCORE_GUI_ELEMENTS, 194, 26, 22, 56);
	public static final EmiTexture TANK_GRADUATION = new EmiTexture(REBORNCORE_GUI_ELEMENTS, 194, 82, 16, 50);

	public static final EmiTexture ARROW_RIGHT_EMPTY = new EmiTexture(REBORNCORE_GUI_ELEMENTS, 58, 150, 16, 10);
	public static final EmiTexture ARROW_RIGHT_FULL = new EmiTexture(REBORNCORE_GUI_ELEMENTS, 74, 150, 16, 10);
	public static final EmiTexture ARROW_LEFT_EMPTY = new EmiTexture(REBORNCORE_GUI_ELEMENTS, 74, 160, 16, 10);
	public static final EmiTexture ARROW_LEFT_FULL = new EmiTexture(REBORNCORE_GUI_ELEMENTS, 58, 160, 16, 10);
	public static final EmiTexture ARROW_UP_EMPTY = new EmiTexture(REBORNCORE_GUI_ELEMENTS, 58, 170, 10, 16);
	public static final EmiTexture ARROW_UP_FULL = new EmiTexture(REBORNCORE_GUI_ELEMENTS, 68, 170, 10, 16);
}
