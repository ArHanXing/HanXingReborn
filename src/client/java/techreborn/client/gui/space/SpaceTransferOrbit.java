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

package techreborn.client.gui.space;

/** Cosmetic cubic transfer arc with prograde departure/arrival tangents, not a flight simulation. */
public record SpaceTransferOrbit(Vec departure, Vec departureControl, Vec arrivalControl, Vec arrival) {
	public record Vec(double x, double y) {
		public Vec add(Vec other) { return new Vec(x + other.x, y + other.y); }
		public Vec subtract(Vec other) { return new Vec(x - other.x, y - other.y); }
		public Vec multiply(double factor) { return new Vec(x * factor, y * factor); }
		public double length() { return Math.hypot(x, y); }
	}

	public static SpaceTransferOrbit between(Vec earth, Vec earthParent, Vec target, Vec targetParent) {
		double distance = target.subtract(earth).length();
		Vec chord = target.subtract(earth);
		Vec departureTangent = prograde(earth.subtract(earthParent), chord);
		Vec arrivalTangent = prograde(target.subtract(targetParent), chord);
		// Keep handles proportional to distance: zoom/pan cannot change the arc.
		double handle = distance * .55;
		return new SpaceTransferOrbit(earth, earth.add(departureTangent.multiply(handle)),
				target.subtract(arrivalTangent.multiply(handle)), target);
	}

	private static Vec prograde(Vec radius, Vec fallback) {
		Vec tangent = radius.length() > 1E-9 ? new Vec(-radius.y(), radius.x()) : fallback;
		double length = tangent.length();
		return length < 1E-9 ? new Vec(0, 0) : tangent.multiply(1 / length);
	}

	public Vec point(double progress) {
		double t = Math.clamp(progress, 0, 1), u = 1 - t;
		return departure.multiply(u * u * u).add(departureControl.multiply(3 * u * u * t))
				.add(arrivalControl.multiply(3 * u * t * t)).add(arrival.multiply(t * t * t));
	}

	public Vec direction(double progress) {
		double t = Math.clamp(progress, 0, 1), u = 1 - t;
		return departureControl.subtract(departure).multiply(3 * u * u)
				.add(arrivalControl.subtract(departureControl).multiply(6 * u * t))
				.add(arrival.subtract(arrivalControl).multiply(3 * t * t));
	}
}
