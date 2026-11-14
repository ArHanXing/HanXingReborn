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

package techreborn.space;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Independent global recipe view; never tied to a machine window. */
public record SpaceRecipeSnapshot(long sequence, boolean invalidate, int part, int parts, byte[] bytes) implements CustomPayload {
	public static final int PART_SIZE = 24_000;
	public static final int MAX_PARTS = 44;
	public static final int MAX_BYTES = PART_SIZE * MAX_PARTS;
	public static final Id<SpaceRecipeSnapshot> ID = new Id<>(Identifier.of("techreborn", "space_recipe_snapshot"));
	public SpaceRecipeSnapshot {
		if (sequence < 1 || parts < 1 || parts > MAX_PARTS || part < 0 || part >= parts || bytes == null || bytes.length > PART_SIZE) {
			throw new IllegalArgumentException("Invalid space recipe fragment");
		}
		bytes = bytes.clone();
	}
	@Override public byte[] bytes() { return bytes.clone(); }
	public static final PacketCodec<RegistryByteBuf, SpaceRecipeSnapshot> CODEC = PacketCodec.ofStatic((buf, value) -> {
		buf.writeLong(value.sequence()); buf.writeBoolean(value.invalidate()); buf.writeVarInt(value.part()); buf.writeVarInt(value.parts()); buf.writeByteArray(value.bytes());
	}, buf -> new SpaceRecipeSnapshot(buf.readLong(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readByteArray(PART_SIZE)));
	@Override public Id<? extends CustomPayload> getId() { return ID; }
}
