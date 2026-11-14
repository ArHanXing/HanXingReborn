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
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/** A window-scoped request. Clients never submit progression, stacks or rewards. */
public record SpaceTerminalRequest(int syncId, BlockPos pos, long catalogRevision, boolean configure,
		String body, int tier, List<String> resources) implements CustomPayload {
	public static final Id<SpaceTerminalRequest> ID = new Id<>(Identifier.of("techreborn", "space_terminal_request"));
	public static final PacketCodec<RegistryByteBuf, SpaceTerminalRequest> CODEC = PacketCodec.ofStatic((buf, value) -> {
		buf.writeVarInt(value.syncId());
		buf.writeBlockPos(value.pos());
		buf.writeLong(value.catalogRevision());
		buf.writeBoolean(value.configure());
		buf.writeString(value.body(), 256);
		buf.writeVarInt(value.tier());
		buf.writeVarInt(value.resources().size());
		value.resources().forEach(id -> buf.writeString(id, 128));
	}, buf -> {
		int syncId = buf.readVarInt();
		BlockPos pos = buf.readBlockPos();
		long revision = buf.readLong();
		boolean configure = buf.readBoolean();
		String body = buf.readString(256);
		int tier = buf.readVarInt();
		int size = buf.readVarInt();
		if (size < 0 || size > 3) {
			throw new IllegalArgumentException("Space selection exceeds three resources");
		}
		List<String> resources = new ArrayList<>();
		for (int i = 0; i < size; i++) {
			resources.add(buf.readString(128));
		}
		return new SpaceTerminalRequest(syncId, pos, revision, configure, body, tier, List.copyOf(resources));
	});

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
