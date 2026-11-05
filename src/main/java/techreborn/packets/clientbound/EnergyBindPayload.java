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

package techreborn.packets.clientbound;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Pushes the state of a bound cable network to the client.
 * <p>
 * The network aggregates (throughput, buffer, time to empty) only exist on the
 * server, while the InGame Info XML tag that displays them is client side, so
 * the numbers have to be sent over. One packet per player per push interval is
 * cheap: it is six numbers, four times a second, for players who opted in with
 * the bind command.
 *
 * @param valid        {@code boolean} whether the bound position still holds a
 *                     cable on a live network
 * @param averageInput {@code long} mean energy pulled in per tick over 5s
 * @param averageOutput {@code long} mean energy delivered per tick over 5s
 * @param buffer       {@code long} energy currently buffered
 * @param capacity     {@code long} total capacity
 * @param ticksToEdge  {@code long} ticks until empty/full, {@code -1} when at
 *                     the edge, {@code Long.MAX_VALUE} when the net flow is zero
 */
public record EnergyBindPayload(boolean valid, long averageInput, long averageOutput, long buffer, long capacity,
		long ticksToEdge) implements CustomPayload {

	public static final Id<EnergyBindPayload> ID = new Id<>(Identifier.of("techreborn", "energy_bind"));
	public static final PacketCodec<RegistryByteBuf, EnergyBindPayload> CODEC = PacketCodec.tuple(
		PacketCodecs.BOOL, EnergyBindPayload::valid,
		PacketCodecs.VAR_LONG, EnergyBindPayload::averageInput,
		PacketCodecs.VAR_LONG, EnergyBindPayload::averageOutput,
		PacketCodecs.VAR_LONG, EnergyBindPayload::buffer,
		PacketCodecs.VAR_LONG, EnergyBindPayload::capacity,
		PacketCodecs.VAR_LONG, EnergyBindPayload::ticksToEdge,
		EnergyBindPayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
