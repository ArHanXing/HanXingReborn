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

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import techreborn.TechReborn;
import techreborn.team.TeamScopes;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Per-machine ownership and future mining selection. No mission or escrow lives
 * here. Server mutation never claims an owner implicitly and never changes an
 * existing owner's scope when its original placer changes teams.
 */
public final class SpaceMachineState {
	public static final String NBT_KEY = "spaceTerminal";
	private static final int MAX_RESOURCE_ID_LENGTH = 256;
	private final Runnable onChanged;
	@Nullable
	private UUID ownerScope;
	/** Malformed owner data must not turn somebody else's machine into free loot. */
	private boolean ownerLocked;
	@Nullable
	private Identifier selectedBody;
	private int selectedVariantTier = 1;
	private List<String> selectedResources = List.of();
	private long revision;

	public SpaceMachineState(Runnable onChanged) {
		this.onChanged = Objects.requireNonNull(onChanged, "onChanged");
	}

	@Nullable
	public UUID ownerScope() {
		return ownerScope;
	}

	public boolean ownerLocked() {
		return ownerLocked;
	}

	@Nullable
	public Identifier selectedBody() {
		return selectedBody;
	}

	public int selectedVariantTier() {
		return selectedVariantTier;
	}

	public List<String> selectedResources() {
		return selectedResources;
	}

	public long revision() {
		return revision;
	}

	public boolean canConfigure(ServerPlayerEntity player) {
		return player != null && !ownerLocked && ownerScope != null && ownerScope.equals(TeamScopes.scopeOf(player));
	}

	/** Only trusted placement/open-screen hooks may call this; never call from a selection packet. */
	public boolean claimIfUnowned(ServerPlayerEntity player) {
		if (player == null || ownerLocked || !player.getServerWorld().getServer().isOnThread()) {
			return false;
		}
		if (ownerScope != null) {
			return canConfigure(player);
		}
		UUID scope = TeamScopes.scopeOf(player);
		if (scope == null) {
			return false;
		}
		ownerScope = scope;
		changed();
		return true;
	}

	/**
	 * Applies an already data-validated selection on the server thread. The caller
	 * must additionally validate current screen, distance, body/variant existence,
	 * resource membership and any mission state. This method only owns structural
	 * validation and frozen-owner permission; an ownerless machine is rejected.
	 *
	 * @return whether the request is authorized and valid, including a no-op
	 */
	public boolean applySelection(ServerPlayerEntity player, @Nullable Identifier body, int tier, List<String> resources) {
		if (!canConfigure(player) || !player.getServerWorld().getServer().isOnThread()
				|| tier < 1 || tier > 3 || resources == null || resources.size() > 3
				|| (body == null && !resources.isEmpty())) {
			return false;
		}
		LinkedHashSet<String> unique = new LinkedHashSet<>();
		for (String resource : resources) {
			if (!validResourceId(resource) || !unique.add(resource)) {
				return false;
			}
		}
		List<String> immutable = List.copyOf(unique);
		if (!Objects.equals(selectedBody, body) || selectedVariantTier != tier || !selectedResources.equals(immutable)) {
			selectedBody = body;
			selectedVariantTier = tier;
			selectedResources = immutable;
			changed();
		}
		return true;
	}

	private static boolean validResourceId(String resource) {
		return resource != null && !resource.isBlank() && resource.length() <= MAX_RESOURCE_ID_LENGTH;
	}

	private void changed() {
		if (revision < Long.MAX_VALUE) {
			revision++;
		}
		onChanged.run();
	}

	/** Loads only its own dedicated compound, leaving all existing machine tags untouched. */
	public void readNbt(NbtCompound root) {
		ownerScope = null;
		ownerLocked = false;
		selectedBody = null;
		selectedVariantTier = 1;
		selectedResources = List.of();
		revision = 0;
		if (!root.contains(NBT_KEY)) {
			return;
		}
		if (!(root.get(NBT_KEY) instanceof NbtCompound tag)) {
			ownerLocked = true;
			TechReborn.LOGGER.warn("Invalid spaceTerminal NBT: ownership locked");
			return;
		}
		ownerLocked = tag.getBoolean("ownerLocked");
		if (tag.contains("ownerScope")) {
			try {
				ownerScope = UUID.fromString(tag.getString("ownerScope"));
			} catch (IllegalArgumentException e) {
				ownerLocked = true;
				TechReborn.LOGGER.warn("Invalid space machine owner UUID: ownership locked");
			}
		}
		if (tag.contains("selectedBody")) {
			String bodyId = tag.getString("selectedBody");
			selectedBody = bodyId.isBlank() ? null : Identifier.tryParse(bodyId);
			if (selectedBody == null) {
				TechReborn.LOGGER.warn("Ignoring invalid selected space body ID");
			}
		}
		if (tag.contains("selectedVariantTier")) {
			int tier = tag.getInt("selectedVariantTier");
			if (tag.contains("selectedVariantTier", NbtElement.INT_TYPE) && tier >= 1 && tier <= 3) {
				selectedVariantTier = tier;
			} else {
				TechReborn.LOGGER.warn("Ignoring invalid selected spacecraft tier");
			}
		}
		if (tag.contains("selectedResources")) {
			if (tag.get("selectedResources") instanceof NbtList list) {
				LinkedHashSet<String> resources = new LinkedHashSet<>();
				for (int i = 0; i < list.size(); i++) {
					NbtElement entry = list.get(i);
					if (!(entry instanceof NbtString) || !validResourceId(entry.asString())
							|| resources.contains(entry.asString()) || resources.size() >= 3) {
						TechReborn.LOGGER.warn("Skipping invalid selected space resource entry {}", i);
						continue;
					}
					resources.add(entry.asString());
				}
				if (selectedBody != null) {
					selectedResources = List.copyOf(resources);
				}
			} else {
				TechReborn.LOGGER.warn("Ignoring invalid selected space resource list");
			}
		}
		if (tag.contains("revision", NbtElement.LONG_TYPE) && tag.getLong("revision") >= 0) {
			revision = tag.getLong("revision");
		}
	}

	public void writeNbt(NbtCompound root) {
		NbtCompound tag = new NbtCompound();
		if (ownerScope != null) {
			tag.putString("ownerScope", ownerScope.toString());
		}
		if (ownerLocked) {
			tag.putBoolean("ownerLocked", true);
		}
		if (selectedBody != null) {
			tag.putString("selectedBody", selectedBody.toString());
		}
		tag.putInt("selectedVariantTier", selectedVariantTier);
		NbtList resources = new NbtList();
		selectedResources.forEach(resource -> resources.add(NbtString.of(resource)));
		tag.put("selectedResources", resources);
		tag.putLong("revision", revision);
		root.put(NBT_KEY, tag);
	}
}
