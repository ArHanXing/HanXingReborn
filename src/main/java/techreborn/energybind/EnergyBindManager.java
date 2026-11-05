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

package techreborn.energybind;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import techreborn.blockentity.cable.CableBlockEntity;
import techreborn.blockentity.cable.CableNetwork;
import techreborn.blockentity.cable.CableNetworkManager;
import techreborn.packets.clientbound.EnergyBindPayload;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side owner of the {@code /techreborn igi_energybind} bindings.
 * <p>
 * A player binds one cable position; from then on the network that cable
 * belongs to is pushed to that player's client on a fixed cadence, which is what
 * the InGame Info XML tag reads. The binding is per player and per world: the
 * command runs in the caller's dimension, and a cable in another dimension has
 * an unrelated network.
 */
public final class EnergyBindManager {

	/** How often the bound network is pushed, in ticks. */
	public static final int PUSH_INTERVAL_TICKS = 5;

	private static final Map<UUID, Binding> BINDINGS = new HashMap<>();

	private EnergyBindManager() {
	}

	/**
	 * @param dimension {@link RegistryKey} the world the bound block is in
	 * @param pos       {@link BlockPos} the bound cable position
	 */
	public record Binding(RegistryKey<World> dimension, BlockPos pos) {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(EnergyBindManager::pushAll);
	}

	/**
	 * Binds a position for a player, replacing any previous binding.
	 *
	 * @param player    {@link ServerPlayerEntity} the player
	 * @param dimension {@link RegistryKey} the world
	 * @param pos       {@link BlockPos} the position
	 */
	public static void bind(ServerPlayerEntity player, RegistryKey<World> dimension, BlockPos pos) {
		BINDINGS.put(player.getUuid(), new Binding(dimension, pos.toImmutable()));
		// Push once immediately so the tag is populated without waiting a tick.
		push(player, BINDINGS.get(player.getUuid()));
	}

	/**
	 * Clears a player's binding.
	 *
	 * @param player {@link ServerPlayerEntity} the player
	 */
	public static void unbind(ServerPlayerEntity player) {
		BINDINGS.remove(player.getUuid());
	}

	/**
	 * @param uuid {@link UUID} the player
	 * @return {@link Binding} the current binding, or {@code null}
	 */
	@Nullable
	public static Binding get(UUID uuid) {
		return BINDINGS.get(uuid);
	}

	private static void pushAll(MinecraftServer server) {
		if (BINDINGS.isEmpty() || server.getTicks() % PUSH_INTERVAL_TICKS != 0) {
			return;
		}
		Iterator<Map.Entry<UUID, Binding>> iterator = BINDINGS.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, Binding> entry = iterator.next();
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
			if (player == null) {
				// Player left: drop the binding so a rejoin starts clean rather
				// than showing stale numbers from a previous session.
				iterator.remove();
				continue;
			}
			push(player, entry.getValue());
		}
	}

	private static void push(ServerPlayerEntity player, Binding binding) {
		EnergyBindPayload payload = resolve(player.getServer(), binding);
		ServerPlayNetworking.send(player, payload);
	}

	/**
	 * Turns a binding into the numbers the client needs. A missing chunk, a
	 * removed cable or a cable outside any assembled network all collapse to
	 * {@code valid = false}, which the client renders as a red fallback rather
	 * than as zeros.
	 *
	 * @param server  {@link MinecraftServer} the server
	 * @param binding {@link Binding} the binding to resolve
	 * @return {@link EnergyBindPayload} the data to send
	 */
	private static EnergyBindPayload resolve(MinecraftServer server, Binding binding) {
		if (server == null) {
			return invalid();
		}
		ServerWorld world = server.getWorld(binding.dimension());
		if (world == null || !world.isChunkLoaded(binding.pos())) {
			return invalid();
		}
		if (!(world.getBlockEntity(binding.pos()) instanceof CableBlockEntity cable)) {
			return invalid();
		}
		CableNetwork network = CableNetworkManager.networkOf(cable);
		if (network == null || network.size() == 0) {
			return invalid();
		}
		return new EnergyBindPayload(true, network.getAverageInput(), network.getAverageOutput(),
				network.getBuffer(), network.getCapacity(), network.ticksUntilBufferEdge());
	}

	private static EnergyBindPayload invalid() {
		return new EnergyBindPayload(false, 0, 0, 0, 0, 0);
	}
}
