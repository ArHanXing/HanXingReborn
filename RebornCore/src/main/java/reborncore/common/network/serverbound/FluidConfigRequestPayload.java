/*
 * This file is part of RebornCore, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021 TeamReborn
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

package reborncore.common.network.serverbound;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import reborncore.common.network.BlockPosPayload;

/**
 * Asks the server to resend one machine's fluid configuration.
 * <p>
 * The config only ever reached the client as a side effect of a save, so a client
 * that had not saved anything yet held whatever the block entity NBT carried -
 * in practice a single "tank 0" holder, even on a machine that reports two tanks.
 * The tank selector then listed the second tank but could not select it. Opening
 * the fluid tab now requests the authoritative holders instead of guessing.
 *
 * @param pos {@link BlockPos} the machine
 */
public record FluidConfigRequestPayload(BlockPos pos) implements CustomPayload, BlockPosPayload {
	public static final Id<FluidConfigRequestPayload> ID = new Id<>(Identifier.of("reborncore:fluid_config_request"));
	public static final PacketCodec<RegistryByteBuf, FluidConfigRequestPayload> PACKET_CODEC = PacketCodec.tuple(
		BlockPos.PACKET_CODEC, FluidConfigRequestPayload::pos,
		FluidConfigRequestPayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
