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

package techreborn.client.gui.space;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import techreborn.space.SpaceTerminalSnapshot;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Only one current window snapshot is assembled; no global cross-server catalogue cache. */
public final class SpaceClientView {
	private static Assembly assembly;

	private SpaceClientView() {
	}

	public static void init() {
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> assembly = null);
		ClientPlayNetworking.registerGlobalReceiver(SpaceTerminalSnapshot.ID, (part, context) -> {
			MinecraftClient client = context.client();
			if (!(client.currentScreen instanceof GuiSpaceTerminal terminal) || client.world == null
					|| terminal.getScreenHandler().syncId != part.syncId()
					|| !terminal.getScreenHandler().getPos().equals(part.pos())) {
				return;
			}
			if (part.sequence() <= terminal.lastSnapshotSequence()
					|| assembly != null && assembly.screen == terminal && part.sequence() < assembly.sequence) {
				return;
			}
			if (assembly == null || assembly.sequence != part.sequence() || assembly.screen != terminal) {
				if (part.sequence() <= terminal.lastSnapshotSequence()) {
					return;
				}
				assembly = new Assembly(part.sequence(), terminal, new byte[part.parts()][]);
			}
			if (assembly.bytes.length != part.parts()) {
				assembly = null;
				return;
			}
			assembly.bytes[part.part()] = part.bytes();
			ByteArrayOutputStream stream = new ByteArrayOutputStream();
			for (byte[] bytes : assembly.bytes) {
				if (bytes == null) {
					return;
				}
				stream.writeBytes(bytes);
			}
			RegistryByteBuf buffer = new RegistryByteBuf(Unpooled.wrappedBuffer(stream.toByteArray()), client.world.getRegistryManager());
			try {
				NbtCompound snapshot = buffer.readNbt();
				if (snapshot != null) {
					terminal.acceptSnapshot(part.sequence(), snapshot);
				}
			} finally {
				buffer.release();
				assembly = null;
			}
		});
	}

	public static Map<String, Body> bodies(NbtCompound root) {
		Map<String, Body> result = new LinkedHashMap<>();
		NbtList bodies = root.getList("bodies", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < bodies.size(); i++) {
			NbtCompound tag = bodies.getCompound(i);
			var markerId = Identifier.tryParse(tag.getString("marker"));
			if (markerId == null || !Registries.BLOCK.containsId(markerId)) {
				continue;
			}
			List<String> ids = new ArrayList<>();
			List<ItemStack> stacks = new ArrayList<>();
			NbtList resources = tag.getList("resources", NbtElement.COMPOUND_TYPE);
			for (int j = 0; j < resources.size(); j++) {
				var entry = resources.getCompound(j);
				var stack = ItemStack.fromNbt(MinecraftClient.getInstance().world.getRegistryManager(), entry.getCompound("stack"));
				if (stack.isPresent()) {
					ids.add(entry.getString("id"));
					stacks.add(stack.get().copyWithCount(entry.getInt("count")));
				}
			}
			SpaceBody body = new SpaceBody(tag.getString("id"), tag.getString("system"),
					tag.getString("parent").isEmpty() ? null : tag.getString("parent"), tag.getString("nameKey"),
					tag.getString("type"), tag.getInt("tier"), Registries.BLOCK.get(markerId), tag.getDouble("orbitRadius"),
					tag.getDouble("phase"), tag.getDouble("markerScale"), stacks, tag.getDouble("radius"), tag.getDouble("temperature"));
			result.put(body.id(), new Body(body, tag, List.copyOf(ids)));
		}
		return java.util.Collections.unmodifiableMap(result);
	}

	public record Body(SpaceBody mapBody, NbtCompound data, List<String> resourceIds) {
		public int stage() {
			return data.getInt("stage");
		}

		public List<Integer> tiers() {
			NbtList list = data.getList("variants", NbtElement.COMPOUND_TYPE);
			List<Integer> tiers = new ArrayList<>();
			for (int i = 0; i < list.size(); i++) {
				tiers.add(list.getCompound(i).getInt("tier"));
			}
			return List.copyOf(tiers);
		}
	}

	private record Assembly(long sequence, GuiSpaceTerminal screen, byte[][] bytes) {
	}
}
