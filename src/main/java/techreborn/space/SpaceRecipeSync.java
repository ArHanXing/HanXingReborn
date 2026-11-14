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

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import techreborn.TechReborn;
import techreborn.space.data.PlanetCatalog;
import techreborn.space.data.PlanetDefinition;
import techreborn.team.TeamScopes;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-only construction of a global, current-viewer recipe catalogue. */
public final class SpaceRecipeSync {
	private static final Map<UUID, Stamp> SENT = new HashMap<>();
	private static long sequence;
	private SpaceRecipeSync() { }
	public static void init() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> SENT.remove(handler.player.getUuid()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SENT.remove(handler.player.getUuid()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SENT.clear());
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTicks() % 20 != 0) { return; }
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) { push(player); }
		});
	}

	private static void push(ServerPlayerEntity player) {
		if (!ServerPlayNetworking.canSend(player, SpaceRecipeSnapshot.ID)) { return; }
		UUID scope = TeamScopes.scopeOf(player);
		SpaceProgressState progress = SpaceProgressState.get(player.getServer());
		Stamp stamp = new Stamp(scope, PlanetCatalog.revision(), progress.revision(scope));
		Stamp previous = SENT.get(player.getUuid());
		if (stamp.equals(previous)) { return; }
		NbtCompound snapshot;
		try {
			snapshot = snapshot(PlanetCatalog.definitions().values().stream().toList(), progress.snapshot(scope), player.getServerWorld().getRegistryManager());
		} catch (RuntimeException exception) {
			TechReborn.LOGGER.error("Cannot encode space recipe view for {}: {}", player.getUuid(), exception.toString());
			snapshot = empty("encoding_failed");
		}
		RegistryByteBuf buffer = new RegistryByteBuf(Unpooled.buffer(), player.getServerWorld().getRegistryManager());
		try {
			buffer.writeNbt(snapshot);
			if (buffer.readableBytes() > SpaceRecipeSnapshot.MAX_BYTES) {
				TechReborn.LOGGER.warn("Space recipe view for {} exceeds {} bytes; sending an explicit empty view, never a partial/stale catalogue", player.getUuid(), SpaceRecipeSnapshot.MAX_BYTES);
				buffer.clear(); buffer.writeNbt(empty("budget_exceeded"));
			}
			byte[] bytes = new byte[buffer.readableBytes()]; buffer.readBytes(bytes);
			int parts = Math.max(1, (bytes.length + SpaceRecipeSnapshot.PART_SIZE - 1) / SpaceRecipeSnapshot.PART_SIZE);
			long serial = ++sequence;
			boolean invalidate = previous == null || !previous.scope().equals(scope);
			for (int part = 0; part < parts; part++) {
				int start = part * SpaceRecipeSnapshot.PART_SIZE;
				ServerPlayNetworking.send(player, new SpaceRecipeSnapshot(serial, invalidate, part, parts,
						Arrays.copyOfRange(bytes, start, Math.min(bytes.length, start + SpaceRecipeSnapshot.PART_SIZE))));
			}
			SENT.put(player.getUuid(), stamp);
		} finally { buffer.release(); }
	}

	public static NbtCompound empty(String reason) {
		NbtCompound root = new NbtCompound(); root.putInt("version", 1); root.putString("reason", reason); root.put("bodies", new NbtList()); return root;
	}

	/** Pure filtered wire view: no machine owner or player-controlled scope argument. */
	public static NbtCompound snapshot(List<PlanetDefinition> definitions, Map<net.minecraft.util.Identifier, Integer> progress,
			RegistryWrapper.WrapperLookup lookup) {
		if (definitions.size() > 256) { throw new IllegalArgumentException("Too many space recipe bodies"); }
		NbtCompound root = empty(""); NbtList bodies = new NbtList();
		for (PlanetDefinition body : definitions) {
			if (body.type().equals("star")) { continue; }
			int stage = Math.clamp(progress.getOrDefault(body.id(), 0), 0, 3);
			NbtCompound view = new NbtCompound();
			view.putString("id", body.id().toString());
			view.putString("marker", net.minecraft.registry.Registries.BLOCK.getId(body.markerBlock()).toString());
			view.putString("nameKey", SpaceTerminalService.visible(body.visibility().name(), stage) ? body.display().nameKey() : "gui.techreborn.space.unknown_body");
			view.putInt("stage", stage);
			NbtList exploration = new NbtList();
			for (int index = 0; index < 3; index++) {
				if (index > stage || body.type().equals("black_hole") && index > 0) { continue; }
				PlanetDefinition.Stage mission = switch (index) { case 0 -> body.missions().survey(); case 1 -> body.missions().land(); default -> body.missions().outpost(); };
				if (!mission.enabled()) { continue; }
				NbtCompound entry = new NbtCompound(); entry.putInt("stage", index + 1); entry.putBoolean("completed", stage > index);
				entry.put("variants", variants(body, index, false, mission.variants(), lookup)); exploration.add(entry);
			}
			view.put("exploration", exploration);
			// Reference visibility follows discovery, not execution prerequisites.
			// A surveyed resource can be looked up before the landing needed to mine it.
			boolean mining = body.missions().mining().enabled() && stage >= 1
					&& SpaceTerminalService.visible(body.visibility().resources(), stage);
			if (mining) {
				view.putBoolean("landingNeeded", body.missions().mining().requiresLanding() && stage < 2);
				view.putBoolean("outpost", stage >= 3 && !body.type().equals("black_hole"));
				view.put("mining", variants(body, stage, true, body.missions().mining().variants(), lookup));
				NbtList resources = new NbtList();
				if (body.resources().size() > 256) { throw new IllegalArgumentException("Too many resource recipes"); }
				for (var resource : body.resources()) {
					NbtCompound entry = new NbtCompound(); entry.putString("id", resource.id());
					ItemStack stack = resource.stack(); int count = view.getBoolean("outpost") ? stack.getCount() : stack.getCount() / 5;
					entry.putInt("count", count); entry.put("stack", stack.copyWithCount(1).encode(lookup)); resources.add(entry);
				}
				view.put("resources", resources);
			}
			if (!exploration.isEmpty() || mining) { bodies.add(view); }
		}
		root.put("bodies", bodies);
		long pages = 0;
		for (int i = 0; i < bodies.size(); i++) {
			NbtCompound body = bodies.getCompound(i);
			NbtList stages = body.getList("exploration", 10);
			for (int j = 0; j < stages.size(); j++) { pages += stages.getCompound(j).getList("variants", 10).size(); }
			pages += (long) body.getList("mining", 10).size() * body.getList("resources", 10).size();
		}
		if (pages > 8192) {
			TechReborn.LOGGER.warn("Space EMI view exceeds8192 expanded pages; sending an explicit empty view");
			return empty("recipe_budget_exceeded");
		}
		return root;
	}

	private static NbtList variants(PlanetDefinition body, int stage, boolean mining, List<PlanetDefinition.Variant> variants,
			RegistryWrapper.WrapperLookup lookup) {
		NbtList list = new NbtList();
		for (var variant : variants) {
			NbtCompound entry = new NbtCompound(); entry.putInt("tier", variant.tier());
			entry.putLong("time", SpaceTerminalService.missionTime(body, stage, mining, variant.tier())); entry.putLong("power", SpaceTerminalService.power(variant.tier()));
			entry.put("inputs", stacks(variant.inputs(), lookup)); entry.put("outputs", stacks(variant.outputs(), lookup)); list.add(entry);
		}
		return list;
	}
	private static NbtList stacks(List<ItemStack> stacks, RegistryWrapper.WrapperLookup lookup) {
		NbtList list = new NbtList();
		for (ItemStack stack : stacks) {
			NbtCompound entry = new NbtCompound(); entry.putInt("count", stack.getCount()); entry.put("stack", stack.copyWithCount(1).encode(lookup)); list.add(entry);
		}
		return list;
	}
	private record Stamp(UUID scope, long catalog, long progress) { }
}
