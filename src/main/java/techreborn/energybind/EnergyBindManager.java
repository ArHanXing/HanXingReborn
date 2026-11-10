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
import techreborn.team.TeamScopes;
import techreborn.world.EnergyBindData;

import java.util.Map;
import java.util.UUID;

/**
 * Server-side driver for the {@code /techreborn igi_energybind} bindings.
 * <p>
 * The bindings themselves live in {@link EnergyBindData} - persisted, and keyed by
 * scope rather than by player - so they survive a relog and are shared by a whole
 * FTB Teams party. This class only resolves and pushes the numbers to whichever
 * members of a scope are online.
 */
public final class EnergyBindManager {

	/** How often the bound network is pushed, in ticks. */
	public static final int PUSH_INTERVAL_TICKS = 5;

	private EnergyBindManager() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(EnergyBindManager::pushAll);
	}

	/**
	 * Binds a position for the player's current scope, replacing any previous
	 * binding.
	 *
	 * @param player    {@link ServerPlayerEntity} the player
	 * @param dimension {@link RegistryKey} the world
	 * @param pos       {@link BlockPos} the position
	 * @return {@link UUID} the scope the binding was stored under
	 */
	public static UUID bind(ServerPlayerEntity player, RegistryKey<World> dimension, BlockPos pos) {
		UUID scope = TeamScopes.scopeOf(player);
		EnergyBindData data = EnergyBindData.get(player.getServerWorld());
		if (data != null) {
			data.put(scope, new EnergyBindData.Binding(dimension, pos.toImmutable()));
		}
		// Push once immediately so the tag is populated without waiting a tick.
		push(player, data == null ? null : data.get(scope));
		return scope;
	}

	/**
	 * Clears the player's scope binding.
	 *
	 * @param player {@link ServerPlayerEntity} the player
	 * @return {@code boolean} whether a binding was removed
	 */
	public static boolean unbind(ServerPlayerEntity player) {
		EnergyBindData data = EnergyBindData.get(player.getServerWorld());
		return data != null && data.remove(TeamScopes.scopeOf(player));
	}

	/**
	 * @param world {@link World} any server world
	 * @param scope {@link UUID} the scope key
	 * @return {@link EnergyBindData.Binding} the binding, or {@code null}
	 */
	@Nullable
	public static EnergyBindData.Binding get(World world, UUID scope) {
		EnergyBindData data = EnergyBindData.get(world);
		return data == null ? null : data.get(scope);
	}

	private static void pushAll(MinecraftServer server) {
		if (server.getTicks() % PUSH_INTERVAL_TICKS != 0) {
			return;
		}
		EnergyBindData data = EnergyBindData.get(server.getOverworld());
		if (data == null || data.entries().isEmpty()) {
			return;
		}
		for (Map.Entry<UUID, EnergyBindData.Binding> entry : data.entries().entrySet()) {
			EnergyBindPayload payload = resolve(server, entry.getValue());
			// Every online member of the scope gets the same numbers, which is the
			// whole point of binding by team instead of by player.
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				if (entry.getKey().equals(TeamScopes.scopeOf(player))) {
					ServerPlayNetworking.send(player, payload);
				}
			}
		}
	}

	private static void push(ServerPlayerEntity player, @Nullable EnergyBindData.Binding binding) {
		ServerPlayNetworking.send(player, resolve(player.getServer(), binding));
	}

	/**
	 * Turns a binding into the numbers the client needs. A missing chunk, a
	 * removed cable or a cable outside any assembled network all collapse to
	 * {@code valid = false}, which the client renders as a red fallback rather
	 * than as zeros.
	 *
	 * @param server  {@link MinecraftServer} the server
	 * @param binding {@link EnergyBindData.Binding} the binding to resolve
	 * @return {@link EnergyBindPayload} the data to send
	 */
	private static EnergyBindPayload resolve(MinecraftServer server, @Nullable EnergyBindData.Binding binding) {
		if (server == null || binding == null) {
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
