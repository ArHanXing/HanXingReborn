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

/** Window-bound task controls; no client-supplied progress, costs or rewards. */
public record SpaceTaskAction(int syncId, BlockPos pos, long catalogRevision, Action action,
		String expectedBody, int expectedTier) implements CustomPayload {
	public enum Action {
		START, TOGGLE_AUTO, TOGGLE_PAUSE
	}

	public SpaceTaskAction {
		if (action == null || expectedBody == null || expectedBody.length() > 256 || expectedTier < 1 || expectedTier > 3) {
			throw new IllegalArgumentException("Invalid space task action");
		}
	}

	public static final Id<SpaceTaskAction> ID = new Id<>(Identifier.of("techreborn", "space_task_action"));
	public static final PacketCodec<RegistryByteBuf, SpaceTaskAction> CODEC = PacketCodec.ofStatic((buf, value) -> {
		buf.writeVarInt(value.syncId());
		buf.writeBlockPos(value.pos());
		buf.writeLong(value.catalogRevision());
		buf.writeVarInt(value.action().ordinal());
		buf.writeString(value.expectedBody(), 256);
		buf.writeVarInt(value.expectedTier());
	}, buf -> {
		int syncId = buf.readVarInt();
		BlockPos pos = buf.readBlockPos();
		long revision = buf.readLong();
		int action = buf.readVarInt();
		if (action < 0 || action >= Action.values().length) {
			throw new IllegalArgumentException("Unknown space task action");
		}
		return new SpaceTaskAction(syncId, pos, revision, Action.values()[action], buf.readString(256), buf.readVarInt());
	});

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
