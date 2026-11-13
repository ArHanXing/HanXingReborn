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

package techreborn.items;

import net.minecraft.item.Item;

import java.util.Objects;

/**
 * Space mission equipment. Fuelled and unfuelled variants are separate registry
 * items; the immutable metadata identifies equipment without parsing its ID.
 */
public class SpacecraftItem extends Item {

	public enum Family {
		DRONE,
		PROBE,
		LANDER,
		CORE
	}

	private final Family family;
	private final int tier;
	private final boolean powered;

	public SpacecraftItem(Family family, int tier, boolean powered) {
		super(new Item.Settings());
		this.family = Objects.requireNonNull(family, "family");
		if (tier < 1 || tier > 3) {
			throw new IllegalArgumentException("Spacecraft tier must be between 1 and 3");
		}
		if (family == Family.CORE && powered) {
			throw new IllegalArgumentException("Outpost cores do not have a fuelled variant");
		}
		this.tier = tier;
		this.powered = powered;
	}

	public Family family() {
		return family;
	}

	public int tier() {
		return tier;
	}

	public boolean powered() {
		return powered;
	}
}
