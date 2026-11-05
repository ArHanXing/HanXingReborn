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

package techreborn.client.compat.igi;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import techreborn.packets.clientbound.EnergyBindPayload;

/**
 * Latest cable-network snapshot pushed by the server for this player.
 * <p>
 * The InGame Info XML tags run every frame, so they must never touch the network
 * or walk the world: they read this cache, which is refreshed by the packet
 * handler a few times a second. Mirrors the guidance in the extension API doc.
 */
@Environment(EnvType.CLIENT)
public final class EnergyBindClientState {

	private static boolean bound = false;
	private static boolean valid = false;
	private static long averageInput = 0;
	private static long averageOutput = 0;
	private static long buffer = 0;
	private static long capacity = 0;
	private static long ticksToEdge = 0;

	private EnergyBindClientState() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(EnergyBindPayload.ID, (payload, context) ->
			context.client().execute(() -> apply(payload)));
		// Leaving a world must clear the binding: the next server has its own.
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
	}

	private static void apply(EnergyBindPayload payload) {
		bound = true;
		valid = payload.valid();
		averageInput = payload.averageInput();
		averageOutput = payload.averageOutput();
		buffer = payload.buffer();
		capacity = payload.capacity();
		ticksToEdge = payload.ticksToEdge();
	}

	public static void clear() {
		bound = false;
		valid = false;
		averageInput = 0;
		averageOutput = 0;
		buffer = 0;
		capacity = 0;
		ticksToEdge = 0;
	}

	/**
	 * @return {@code boolean} whether the server has ever pushed a binding
	 */
	public static boolean isBound() {
		return bound;
	}

	/**
	 * @return {@code boolean} whether the bound cable still has a live network
	 */
	public static boolean isValid() {
		return bound && valid;
	}

	public static long getAverageInput() {
		return averageInput;
	}

	public static long getAverageOutput() {
		return averageOutput;
	}

	public static long getBuffer() {
		return buffer;
	}

	public static long getCapacity() {
		return capacity;
	}

	/**
	 * @return {@code long} ticks until empty/full, {@code -1} at the edge,
	 *         {@code Long.MAX_VALUE} when the net flow is zero
	 */
	public static long getTicksToEdge() {
		return ticksToEdge;
	}
}
