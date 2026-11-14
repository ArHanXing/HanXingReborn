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

package techreborn.client.compat.emi;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import techreborn.TechReborn;
import techreborn.space.SpaceRecipeSnapshot;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Filtered client cache exists without EMI. Optional EMI linkage is isolated in Bridge. */
public final class SpaceEmiData {
	private static volatile List<RecipeView> recipes = List.of();
	private static volatile long revision;
	private static volatile long registeredRevision = -1;
	private static Assembly assembly;
	private static long lastSequence;
	private static boolean initialized;
	private static boolean reloadRequested;
	private static int retryTicks;
	private SpaceEmiData() { }

	public static void init() {
		if (initialized) { return; } initialized = true;
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			assembly = null; lastSequence = 0; publish(List.of()); reloadRequested = false; registeredRevision = -1;
		});
		ClientPlayNetworking.registerGlobalReceiver(SpaceRecipeSnapshot.ID, (part, context) -> accept(part, context.client()));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.world != null && FabricLoader.getInstance().isModLoaded("emi") && ++retryTicks % 10 == 0) { Bridge.refresh(); }
		});
	}

	private static void accept(SpaceRecipeSnapshot part, MinecraftClient client) {
		if (client.world == null || part.sequence() <= lastSequence || assembly != null && part.sequence() < assembly.sequence) { return; }
		if (assembly == null || part.sequence() != assembly.sequence) {
			assembly = new Assembly(part.sequence(), part.invalidate(), new byte[part.parts()][]);
			if (part.invalidate()) { publish(List.of()); }
		}
		if (part.parts() != assembly.bytes.length || part.invalidate() != assembly.invalidate) { assembly = null; return; }
		assembly.bytes[part.part()] = part.bytes();
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		for (byte[] bytes : assembly.bytes) { if (bytes == null) { return; } stream.writeBytes(bytes); }
		if (stream.size() > SpaceRecipeSnapshot.MAX_BYTES) { assembly = null; publish(List.of()); return; }
		RegistryByteBuf buffer = new RegistryByteBuf(Unpooled.wrappedBuffer(stream.toByteArray()), client.world.getRegistryManager());
		try {
			NbtCompound root = buffer.readNbt();
			if (root == null || buffer.isReadable()) { throw new IllegalArgumentException("Invalid recipe snapshot framing"); }
			List<RecipeView> parsed = decode(root, client.world.getRegistryManager());
			publish(parsed); lastSequence = part.sequence();
			if (!root.getString("reason").isEmpty()) { TechReborn.LOGGER.warn("Space recipe view is empty: {}", root.getString("reason")); }
		} catch (RuntimeException exception) {
			// Never retain recipes from a now-revoked viewer scope on malformed input.
			publish(List.of()); lastSequence = part.sequence();
			TechReborn.LOGGER.warn("Rejected space recipe snapshot: {}", exception.toString());
		} finally { buffer.release(); assembly = null; }
	}

	private static void publish(List<RecipeView> replacement) {
		if (!recipes.equals(replacement)) { recipes = List.copyOf(replacement); revision++; reloadRequested = false; }
	}
	public static List<RecipeView> recipes() { return recipes; }

	/** Keep the workstation discoverable without revealing any hidden recipe data. */
	public static boolean needsMiningInfo(List<RecipeView> visible) {
		return visible.stream().noneMatch(recipe -> recipe.kind().equals("mining"));
	}

	public static List<List<RecipeView>> explorationGroups(List<RecipeView> visible) {
		java.util.Map<Identifier, List<RecipeView>> groups = new java.util.LinkedHashMap<>();
		for (RecipeView recipe : visible) {
			if (!recipe.kind().equals("mining")) { groups.computeIfAbsent(recipe.bodyId(), ignored -> new ArrayList<>()).add(recipe); }
		}
		return groups.values().stream().map(List::copyOf).toList();
	}

	/** All limits checked before publication; no partially decoded recipe catalogue. */
	public static List<RecipeView> decode(NbtCompound root, RegistryWrapper.WrapperLookup lookup) {
		if (root.getInt("version") != 1) { throw new IllegalArgumentException("Unknown space recipe format"); }
		NbtList bodies = root.getList("bodies", NbtElement.COMPOUND_TYPE);
		if (bodies.size() > 256) { throw new IllegalArgumentException("Too many recipe bodies"); }
		List<RecipeView> result = new ArrayList<>(); Set<Identifier> ids = new HashSet<>();
		for (int i = 0; i < bodies.size(); i++) {
			NbtCompound body = bodies.getCompound(i); String bodyId = text(body, "id", 256); Identifier.of(bodyId);
			String name = text(body, "nameKey", 512);
			Identifier marker = Identifier.of(text(body, "marker", 256));
			if (!net.minecraft.registry.Registries.BLOCK.containsId(marker)) { throw new IllegalArgumentException("Unknown body marker"); }
			NbtList exploration = body.getList("exploration", NbtElement.COMPOUND_TYPE);
			if (exploration.size() > 3) { throw new IllegalArgumentException("Too many exploration stages"); }
			for (int j = 0; j < exploration.size(); j++) {
				NbtCompound mission = exploration.getCompound(j); int stage = mission.getInt("stage");
				if (stage < 1 || stage > 3) { throw new IllegalArgumentException("Invalid exploration stage"); }
				String kind = switch (stage) { case 1 -> "survey"; case 2 -> "land"; default -> "outpost"; };
				for (Variant variant : variants(mission.getList("variants", NbtElement.COMPOUND_TYPE), lookup, 9, 9)) {
					result.add(new RecipeView(id(bodyId, kind, variant.tier, ""), Identifier.of(bodyId), name, marker, kind, mission.getBoolean("completed"), false, false,
							variant.tier, variant.power, variant.time, variant.inputs, variant.outputs, ItemStack.EMPTY, 0));
				}
			}
			NbtList resources = body.getList("resources", NbtElement.COMPOUND_TYPE);
			if (resources.size() > 256) { throw new IllegalArgumentException("Too many recipe resources"); }
			List<Variant> mining = variants(body.getList("mining", NbtElement.COMPOUND_TYPE), lookup, 4, 6);
			for (int j = 0; j < resources.size(); j++) {
				NbtCompound resource = resources.getCompound(j); String resourceId = text(resource, "id", 256);
				int count = resource.getInt("count"); if (count < 0) { throw new IllegalArgumentException("Negative resource yield"); }
				ItemStack stack = unitStack(resource, lookup);
				for (Variant variant : mining) {
					result.add(new RecipeView(id(bodyId, "mining", variant.tier, resourceId), Identifier.of(bodyId), name, marker, "mining", false, body.getBoolean("outpost"), body.getBoolean("landingNeeded"),
							variant.tier, variant.power, variant.time, variant.inputs, variant.outputs, stack, count));
				}
			}
			if (result.size() > 8192) { throw new IllegalArgumentException("Too many expanded space recipes"); }
		}
		for (RecipeView recipe : result) { if (!ids.add(recipe.id)) { throw new IllegalArgumentException("Duplicate space recipe id"); } }
		return List.copyOf(result);
	}

	private static List<Variant> variants(NbtList list, RegistryWrapper.WrapperLookup lookup, int inputs, int outputs) {
		if (list.size() > 3) { throw new IllegalArgumentException("Too many space variants"); }
		List<Variant> result = new ArrayList<>(); Set<Integer> tiers = new HashSet<>();
		for (int i = 0; i < list.size(); i++) {
			NbtCompound tag = list.getCompound(i); int tier = tag.getInt("tier"); long power = tag.getLong("power"), time = tag.getLong("time");
			if (tier < 1 || tier > 3 || !tiers.add(tier) || power <= 0 || time <= 0) { throw new IllegalArgumentException("Invalid space variant"); }
			result.add(new Variant(tier, power, time, stacks(tag.getList("inputs", NbtElement.COMPOUND_TYPE), inputs, lookup), stacks(tag.getList("outputs", NbtElement.COMPOUND_TYPE), outputs, lookup)));
		}
		return result;
	}
	private static List<ItemStack> stacks(NbtList list, int limit, RegistryWrapper.WrapperLookup lookup) {
		if (list.size() > limit) { throw new IllegalArgumentException("Too many recipe stacks"); }
		List<ItemStack> result = new ArrayList<>();
		for (int i = 0; i < list.size(); i++) {
			NbtCompound entry = list.getCompound(i); int count = entry.getInt("count");
			if (count < 1) { throw new IllegalArgumentException("Invalid recipe stack count"); }
			result.add(unitStack(entry, lookup).copyWithCount(count));
		}
		return List.copyOf(result);
	}
	private static ItemStack unitStack(NbtCompound entry, RegistryWrapper.WrapperLookup lookup) {
		ItemStack stack = ItemStack.fromNbt(lookup, entry.getCompound("stack")).orElseThrow(() -> new IllegalArgumentException("Invalid recipe item"));
		if (stack.isEmpty() || stack.getCount() != 1) { throw new IllegalArgumentException("Recipe wire stack must have unit count"); }
		return stack;
	}
	private static String text(NbtCompound tag, String key, int max) {
		String value = tag.getString(key); if (value.isBlank() || value.length() > max) { throw new IllegalArgumentException("Invalid recipe string: " + key); } return value;
	}
	private static Identifier id(String body, String kind, int tier, String resource) {
		// Hex keeps stable arbitrary resource IDs collision-free without illegal path characters.
		String encoded = java.util.HexFormat.of().formatHex((body + "\n" + resource).getBytes(StandardCharsets.UTF_8));
		return Identifier.of("techreborn", "/space/" + kind + "/" + tier + "/" + encoded);
	}
	private static List<ItemStack> copies(List<ItemStack> stacks) { return stacks.stream().map(ItemStack::copy).toList(); }
	public record RecipeView(Identifier id, Identifier bodyId, String nameKey, Identifier marker, String kind, boolean completed, boolean outpost, boolean landingNeeded, int tier,
			long power, long time, List<ItemStack> inputs, List<ItemStack> returns, ItemStack resource, int yield) {
		public RecipeView { inputs = copies(inputs); returns = copies(returns); resource = resource.copy(); }
		@Override public List<ItemStack> inputs() { return copies(inputs); }
		@Override public List<ItemStack> returns() { return copies(returns); }
		@Override public ItemStack resource() { return resource.copy(); }
		@Override public boolean equals(Object other) {
			if (!(other instanceof RecipeView that)) { return false; }
			return id.equals(that.id) && bodyId.equals(that.bodyId) && nameKey.equals(that.nameKey) && marker.equals(that.marker) && kind.equals(that.kind) && completed == that.completed && outpost == that.outpost && landingNeeded == that.landingNeeded && tier == that.tier
					&& power == that.power && time == that.time && yield == that.yield && ItemStack.areEqual(resource, that.resource) && stackListsEqual(inputs, that.inputs) && stackListsEqual(returns, that.returns);
		}
		@Override public int hashCode() { return id.hashCode(); }
		private static boolean stackListsEqual(List<ItemStack> a, List<ItemStack> b) {
			if (a.size() != b.size()) { return false; } for (int i = 0; i < a.size(); i++) { if (!ItemStack.areEqual(a.get(i), b.get(i))) { return false; } } return true;
		}
	}
	private record Variant(int tier, long power, long time, List<ItemStack> inputs, List<ItemStack> outputs) { }
	private record Assembly(long sequence, boolean invalidate, byte[][] bytes) { }

	/** Loaded only when EMI is installed or EMI itself invokes our plugin. */
	public static final class Bridge {
		private Bridge() { }
		public static void register(dev.emi.emi.api.EmiRegistry registry) {
			long registering = revision; List<RecipeView> current = recipes;
			for (RecipeView recipe : current) {
				if (recipe.kind().equals("mining")) { registry.addRecipe(new SpaceMissionEmiRecipe(recipe)); }
			}
			for (var group : explorationGroups(current)) { registry.addRecipe(new SpaceExplorationEmiRecipe(group)); }
			if (needsMiningInfo(current)) { registry.addRecipe(SpaceMissionEmiRecipe.miningInfo()); }
			registeredRevision = registering;
		}
		private static boolean unavailable;
		private static java.lang.reflect.Method isLoaded;
		private static java.lang.reflect.Method reload;
		private static void refresh() {
			if (registeredRevision == revision) { reloadRequested = false; return; }
			if (unavailable || reloadRequested) { return; }
			try {
				// EMI's compile API intentionally excludes runtime classes. These exact
				// methods were verified in the pinned1.1.22 runtime/source jar.
				if (isLoaded == null) {
					Class<?> manager = Class.forName("dev.emi.emi.runtime.EmiReloadManager");
					isLoaded = manager.getMethod("isLoaded"); reload = manager.getMethod("reload");
				}
				if (Boolean.TRUE.equals(isLoaded.invoke(null))) { reloadRequested = true; reload.invoke(null); }
			} catch (ReflectiveOperationException | LinkageError exception) {
				unavailable = true;
				TechReborn.LOGGER.warn("Cannot refresh EMI space recipes with the pinned reload bridge: {}", exception.toString());
			}
		}
	}
}
