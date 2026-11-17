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
import techreborn.TechReborn;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

/** Resolves player-owned persistent data to an FTB party UUID when available. */
public final class TeamScopes {

	private static final String FTB_TEAMS = "ftbteams";
	private static final String API_CLASS = "dev.ftb.mods.ftbteams.api.FTBTeamsAPI";
	private static final String API_CONTRACT = API_CLASS + "$API";
	private static final String MANAGER_CONTRACT = "dev.ftb.mods.ftbteams.api.TeamManager";
	private static final String TEAM_CONTRACT = "dev.ftb.mods.ftbteams.api.Team";

	private static final boolean FTB_LOADED = FabricLoader.getInstance().isModLoaded(FTB_TEAMS);
	/** Structural API failures disable team resolution; manager-not-ready does not. */
	private static volatile boolean ftbUsable = FTB_LOADED;
	private static volatile boolean ftbWarningLogged;

	private TeamScopes() {
	}

	/**
	 * @return whether FTB team resolution is available or may still become available
	 */
	public static boolean isTeamScopingActive() {
		return ftbUsable;
	}

	/**
	 * @param player player UUID
	 * @return party UUID if the player belongs to a party, otherwise their UUID
	 */
	public static UUID scopeOf(UUID player) {
		if (player == null || !ftbUsable) {
			return player;
		}
		try {
			PartyResolution resolution = resolvePartyId(player, TeamScopes.class.getClassLoader());
			return resolution.partyId() == null ? player : resolution.partyId();
		} catch (ManagerNotReadyException ignored) {
			// FTB Teams explicitly permits its manager to be absent before startup.
			// Use personal scope for this call and retry on the next scope lookup.
			return player;
		} catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException
				| IllegalArgumentException | ClassCastException | LinkageError exception) {
			disableFtb("Incompatible FTB Teams public API", exception);
			return player;
		} catch (InvocationTargetException exception) {
			Throwable cause = exception.getCause();
			if (cause instanceof NullPointerException) {
				// FTBTeamsAPI.api()/getManager() use NPE for not-yet-initialized state.
				return player;
			}
			if (cause instanceof LinkageError linkage) {
				disableFtb("FTB Teams API linkage failure", linkage);
				return player;
			}
			warnFtbOnce("FTB Teams lookup failed; using personal scope for this lookup", cause == null ? exception : cause);
			return player;
		} catch (ReflectiveOperationException exception) {
			disableFtb("Incompatible FTB Teams public API", exception);
			return player;
		} catch (RuntimeException exception) {
			warnFtbOnce("FTB Teams lookup failed; using personal scope for this lookup", exception);
			return player;
		}
	}

	/**
	 * @param player server player
	 * @return scope key for that player
	 */
	public static UUID scopeOf(ServerPlayerEntity player) {
		return player == null ? null : scopeOf(player.getUuid());
	}

	/**
	 * @param server server
	 * @param scope scope key
	 * @return any online member of that scope, or {@code null}
	 */
	@Nullable
	public static ServerPlayerEntity anyOnlineMember(MinecraftServer server, UUID scope) {
		if (server == null || scope == null) {
			return null;
		}
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

	/** Package-private seam for focused resolver tests using a fake API class loader. */
	static PartyResolution resolvePartyId(UUID player, ClassLoader loader)
			throws ReflectiveOperationException, ManagerNotReadyException {
		Class<?> apiClass = Class.forName(API_CLASS, true, loader);
		Class<?> apiContract = Class.forName(API_CONTRACT, true, loader);
		Class<?> managerContract = Class.forName(MANAGER_CONTRACT, true, loader);
		Class<?> teamContract = Class.forName(TEAM_CONTRACT, true, loader);

		Object api = apiClass.getMethod("api").invoke(null);
		if (api == null) {
			throw new ManagerNotReadyException();
		}
		Method isManagerLoaded = apiContract.getMethod("isManagerLoaded");
		if (!Boolean.TRUE.equals(isManagerLoaded.invoke(api))) {
			throw new ManagerNotReadyException();
		}
		Object manager = apiContract.getMethod("getManager").invoke(api);
		if (manager == null) {
			throw new ManagerNotReadyException();
		}
		Object result = managerContract.getMethod("getTeamForPlayerID", UUID.class).invoke(manager, player);
		if (!(result instanceof Optional<?> teamOptional)) {
			throw new ClassCastException("FTB Teams getTeamForPlayerID did not return Optional");
		}
		if (teamOptional.isEmpty()) {
			return new PartyResolution(true, null);
		}
		Object team = teamOptional.get();
		if (team == null) {
			throw new ClassCastException("FTB Teams returned a null team inside Optional");
		}
		// Invoke methods declared by public API contracts. Calling getMethod() on
		// a package-private implementation class can still fail access checks.
		Object isParty = teamContract.getMethod("isPartyTeam").invoke(team);
		if (!Boolean.TRUE.equals(isParty)) {
			return new PartyResolution(true, null);
		}
		Object id = teamContract.getMethod("getId").invoke(team);
		if (!(id instanceof UUID partyId)) {
			throw new ClassCastException("FTB Teams Team.getId() did not return UUID");
		}
		return new PartyResolution(true, partyId);
	}

	private static void disableFtb(String message, Throwable cause) {
		ftbUsable = false;
		warnFtbOnce(message + "; team scoping disabled", cause);
	}

	private static void warnFtbOnce(String message, Throwable cause) {
		if (!ftbWarningLogged) {
			synchronized (TeamScopes.class) {
				if (!ftbWarningLogged) {
					TechReborn.LOGGER.warn(message, cause);
					ftbWarningLogged = true;
				}
			}
		}
	}

	static record PartyResolution(boolean managerReady, @Nullable UUID partyId) {
	}

	static final class ManagerNotReadyException extends Exception {
		ManagerNotReadyException() {
			super("FTB Teams manager is not ready");
		}
	}
}
