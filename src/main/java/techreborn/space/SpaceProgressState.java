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

package techreborn.space;

import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import techreborn.TechReborn;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Server-owned, monotonic team progress. Keys are already-resolved scope IDs. */
public final class SpaceProgressState extends PersistentState {
	public static final int UNKNOWN = 0;
	public static final int SURVEYED = 1;
	public static final int LANDED = 2;
	public static final int OUTPOST = 3;
	private static final String DATA_NAME = "techreborn_space_progress";
	private static final Type<SpaceProgressState> TYPE = new Type<>(SpaceProgressState::new,
			SpaceProgressState::fromNbt, DataFixTypes.SAVED_DATA_MAP_DATA);
	private final Map<UUID, Map<Identifier, Integer>> progress = new HashMap<>();
	private final Map<UUID, Long> revisions = new HashMap<>();

	private SpaceProgressState() {
	}

	@Nullable
	public static SpaceProgressState get(World world) {
		return world == null || world.isClient ? null : get(world.getServer());
	}

	@Nullable
	public static SpaceProgressState get(MinecraftServer server) {
		if (server == null || server.getOverworld() == null) {
			return null;
		}
		return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, DATA_NAME);
	}

	public int stage(UUID scope, Identifier body) {
		return scope == null || body == null ? UNKNOWN : progress.getOrDefault(scope, Map.of()).getOrDefault(body, UNKNOWN);
	}

	public Map<Identifier, Integer> snapshot(UUID scope) {
		return scope == null ? Map.of() : Map.copyOf(progress.getOrDefault(scope, Map.of()));
	}

	public long revision(UUID scope) {
		return revisions.getOrDefault(scope, 0L);
	}

	/**
	 * Trusted server integration API for a future executor or operator command.
	 * Never invoke this from a normal-player request handler. Stages may only grow;
	 * prerequisites/planet existence must be checked by the caller.
	 */
	public static boolean advance(ServerWorld world, UUID scope, Identifier body, int stage) {
		return advance(world.getServer(), scope, body, stage);
	}

	public static boolean advance(MinecraftServer server, UUID scope, Identifier body, int stage) {
		Objects.requireNonNull(server, "server");
		Objects.requireNonNull(scope, "scope");
		Objects.requireNonNull(body, "body");
		if (!server.isOnThread()) {
			throw new IllegalStateException("Space progression must advance on the server thread");
		}
		if (stage < UNKNOWN || stage > OUTPOST) {
			throw new IllegalArgumentException("Space stage must be 0..3");
		}
		SpaceProgressState state = get(server);
		if (state == null || stage <= state.stage(scope, body)) {
			return false;
		}
		long oldRevision = state.revision(scope);
		if (oldRevision == Long.MAX_VALUE) {
			throw new IllegalStateException("Space progress revision exhausted");
		}
		state.progress.computeIfAbsent(scope, ignored -> new HashMap<>()).put(body, stage);
		state.revisions.put(scope, oldRevision + 1);
		state.markDirty();
		return true;
	}

	static SpaceProgressState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		SpaceProgressState state = new SpaceProgressState();
		NbtElement rawScopes = nbt.get("scopes");
		if (rawScopes == null) {
			return state;
		}
		if (!(rawScopes instanceof NbtList scopes)) {
			TechReborn.LOGGER.warn("Ignoring corrupt space progression scopes list");
			return state;
		}
		for (int i = 0; i < scopes.size(); i++) {
			try {
				if (!(scopes.get(i) instanceof NbtCompound entry)) {
					throw new IllegalArgumentException("scope entry is not a compound");
				}
				UUID scope = UUID.fromString(entry.getString("scope"));
				long revision = entry.getLong("revision");
				if (revision < 0 || !entry.contains("revision", NbtElement.LONG_TYPE)) {
					throw new IllegalArgumentException("invalid scope revision");
				}
				if (!(entry.get("bodies") instanceof NbtList bodies)) {
					throw new IllegalArgumentException("missing body entries");
				}
				Map<Identifier, Integer> stages = state.progress.computeIfAbsent(scope, ignored -> new HashMap<>());
				state.revisions.merge(scope, revision, Math::max);
				for (int j = 0; j < bodies.size(); j++) {
					try {
						if (!(bodies.get(j) instanceof NbtCompound bodyEntry)) {
							throw new IllegalArgumentException("body entry is not a compound");
						}
						String bodyId = bodyEntry.getString("id");
						Identifier body = bodyId.isBlank() ? null : Identifier.tryParse(bodyId);
						int stage = bodyEntry.getInt("stage");
						if (body == null || !bodyEntry.contains("stage", NbtElement.INT_TYPE) || stage < UNKNOWN || stage > OUTPOST) {
							throw new IllegalArgumentException("invalid body ID or stage");
						}
						if (stage != UNKNOWN) {
							stages.merge(body, stage, Math::max);
						}
					} catch (IllegalArgumentException e) {
						TechReborn.LOGGER.warn("Skipping corrupt space progression body {} in scope {}: {}", j, scope, e.getMessage());
					}
				}
			} catch (IllegalArgumentException e) {
				TechReborn.LOGGER.warn("Skipping corrupt space progression scope {}: {}", i, e.getMessage());
			}
		}
		return state;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		NbtList scopes = new NbtList();
		progress.forEach((scope, stages) -> {
			NbtCompound entry = new NbtCompound();
			entry.putString("scope", scope.toString());
			entry.putLong("revision", revision(scope));
			NbtList bodies = new NbtList();
			stages.forEach((body, stage) -> {
				NbtCompound bodyEntry = new NbtCompound();
				bodyEntry.putString("id", body.toString());
				bodyEntry.putInt("stage", stage);
				bodies.add(bodyEntry);
			});
			entry.put("bodies", bodies);
			scopes.add(entry);
		});
		nbt.put("scopes", scopes);
		return nbt;
	}
}
