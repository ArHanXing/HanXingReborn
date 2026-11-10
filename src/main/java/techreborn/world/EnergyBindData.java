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

package techreborn.world;

import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persisted cable-network bindings for the InGame Info XML tags.
 * <p>
 * Keyed by <em>scope</em>: a player's UUID normally, or their FTB Teams party id
 * when they are in one (see {@link techreborn.team.TeamScopes}). Storing by scope
 * rather than by player is what makes the binding survive a relog and what lets a
 * whole team share one binding instead of each member redoing the command.
 * <p>
 * The data lives in the overworld's level storage, so it is server-wide and
 * survives restarts.
 */
public class EnergyBindData extends PersistentState {

	private static final String DATA_NAME = "techreborn_energy_bind";

	private static final Type<EnergyBindData> TYPE = new Type<>(EnergyBindData::new,
			EnergyBindData::fromNbt, DataFixTypes.SAVED_DATA_MAP_DATA);

	/** One binding: which dimension and which cable position. */
	public record Binding(RegistryKey<World> dimension, BlockPos pos) {
	}

	/** Scope id to binding. */
	private final Map<UUID, Binding> bindings = new HashMap<>();

	private EnergyBindData() {
	}

	/**
	 * @param world {@link World} any server world
	 * @return the shared data, or {@code null} on the client or before the server
	 *         is fully started
	 */
	@Nullable
	public static EnergyBindData get(World world) {
		if (world == null || world.isClient) {
			return null;
		}
		MinecraftServer server = world.getServer();
		if (server == null) {
			return null;
		}
		ServerWorld overworld = server.getOverworld();
		if (overworld == null) {
			return null;
		}
		PersistentStateManager manager = overworld.getPersistentStateManager();
		return manager.getOrCreate(TYPE, DATA_NAME);
	}

	/**
	 * @param scope {@link UUID} the scope key
	 * @return {@link Binding} the binding, or {@code null} when unbound
	 */
	@Nullable
	public Binding get(UUID scope) {
		return scope == null ? null : bindings.get(scope);
	}

	/**
	 * Stores a binding, replacing any previous one for the same scope.
	 *
	 * @param scope   {@link UUID} the scope key
	 * @param binding {@link Binding} the binding
	 */
	public void put(UUID scope, Binding binding) {
		if (scope == null || binding == null) {
			return;
		}
		bindings.put(scope, binding);
		markDirty();
	}

	/**
	 * Removes a scope's binding.
	 *
	 * @param scope {@link UUID} the scope key
	 * @return {@code boolean} whether anything was removed
	 */
	public boolean remove(UUID scope) {
		if (scope == null || bindings.remove(scope) == null) {
			return false;
		}
		markDirty();
		return true;
	}

	/**
	 * @return the whole map, used by the push loop
	 */
	public Map<UUID, Binding> entries() {
		return bindings;
	}

	private static EnergyBindData fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		EnergyBindData data = new EnergyBindData();
		NbtList list = nbt.getList("bindings", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < list.size(); i++) {
			NbtCompound entry = list.getCompound(i);
			if (!entry.contains("scope") || !entry.contains("dimension") || !entry.contains("pos")) {
				continue;
			}
			try {
				UUID scope = UUID.fromString(entry.getString("scope"));
				RegistryKey<World> dimension = RegistryKey.of(RegistryKeys.WORLD,
						Identifier.tryParse(entry.getString("dimension")));
				BlockPos pos = BlockPos.fromLong(entry.getLong("pos"));
				data.bindings.put(scope, new Binding(dimension, pos));
			} catch (IllegalArgumentException ignored) {
				// Corrupt entry: drop it rather than fail the whole save file.
			}
		}
		return data;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		NbtList list = new NbtList();
		bindings.forEach((scope, binding) -> {
			NbtCompound entry = new NbtCompound();
			entry.putString("scope", scope.toString());
			entry.putString("dimension", binding.dimension().getValue().toString());
			entry.putLong("pos", binding.pos().asLong());
			list.add(entry);
		});
		nbt.put("bindings", list);
		return nbt;
	}
}
