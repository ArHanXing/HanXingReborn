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

package techreborn.team;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the key that player-owned persistent data should be stored under.
 * <p>
 * On a multiplayer server the unit of ownership is usually the team, not the
 * individual: a shared IDSU, a shared Dyson Swarm and a shared cable binding are
 * all things a team builds together, and keying them by player UUID means a
 * teammate sees nothing until they re-do the binding themselves.
 * <p>
 * When FTB Teams is installed, a player who is in a party is keyed by that
 * party's id; everyone else is keyed by their own UUID. Without FTB Teams the
 * resolver always returns the player's own UUID, so single-player and
 * FTB-less servers behave exactly as before.
 * <p>
 * The FTB classes are only touched reflectively after an {@code isModLoaded}
 * check, so this module keeps compiling and running without FTB Teams present.
 */
public final class TeamScopes {

	private static final String FTB_TEAMS = "ftbteams";

	private static final boolean FTB_LOADED = FabricLoader.getInstance().isModLoaded(FTB_TEAMS);

	/**
	 * Cached per-session flag: the first reflective failure (API shape changed,
	 * manager not ready) disables team resolution instead of retrying every tick.
	 */
	private static boolean ftbUsable = FTB_LOADED;

	private TeamScopes() {
	}

	/**
	 * @return {@code boolean} whether team scoping is actually in effect
	 */
	public static boolean isTeamScopingActive() {
		return ftbUsable;
	}

	/**
	 * @param player {@link UUID} the player
	 * @return {@link UUID} the team id when the player is in a party, the player's
	 *         own id otherwise
	 */
	public static UUID scopeOf(UUID player) {
		if (player == null || !ftbUsable) {
			return player;
		}
		UUID team = ftbPartyId(player);
		return team != null ? team : player;
	}

	/**
	 * @param player {@link ServerPlayerEntity} the player
	 * @return {@link UUID} the scope key for that player
	 */
	public static UUID scopeOf(ServerPlayerEntity player) {
		return player == null ? null : scopeOf(player.getUuid());
	}

	/**
	 * @param server {@link MinecraftServer} the server, may be {@code null}
	 * @param scope  {@link UUID} the scope key to resolve back to a player
	 * @return {@link ServerPlayerEntity} an online member of that scope, or
	 *         {@code null} when nobody is online
	 */
	@Nullable
	public static ServerPlayerEntity anyOnlineMember(MinecraftServer server, UUID scope) {
		if (server == null || scope == null) {
			return null;
		}
		// Fast path: the scope is a plain player key.
		ServerPlayerEntity direct = server.getPlayerManager().getPlayer(scope);
		if (direct != null) {
			return direct;
		}
		if (!ftbUsable) {
			return null;
		}
		for (ServerPlayerEntity candidate : server.getPlayerManager().getPlayerList()) {
			if (scope.equals(scopeOf(candidate))) {
				return candidate;
			}
		}
		return null;
	}

	/**
	 * Asks FTB Teams for the player's party id.
	 *
	 * @param player {@link UUID} the player
	 * @return {@link UUID} the party id, or {@code null} when the player has no
	 *         party or FTB Teams could not be queried
	 */
	@Nullable
	private static UUID ftbPartyId(UUID player) {
		try {
			Class<?> apiClass = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI");
			Object api = apiClass.getMethod("api").invoke(null);
			if (api == null) {
				return null;
			}
			Object manager = api.getClass().getMethod("getManager").invoke(api);
			if (manager == null) {
				// Team manager is not up yet (early load); leave it for next call.
				return null;
			}
			Optional<?> teamOptional = (Optional<?>) manager.getClass()
				.getMethod("getTeamForPlayerID", UUID.class).invoke(manager, player);
			if (teamOptional == null || teamOptional.isEmpty()) {
				return null;
			}
			Object team = teamOptional.get();
			// Only a party is a shared scope; a player team is just the player.
			Object isParty = team.getClass().getMethod("isPartyTeam").invoke(team);
			if (!Boolean.TRUE.equals(isParty)) {
				return null;
			}
			return (UUID) team.getClass().getMethod("getId").invoke(team);
		} catch (ReflectiveOperationException | LinkageError e) {
			// API moved or missing: fall back to per-player keys permanently.
			ftbUsable = false;
			return null;
		}
	}
}
