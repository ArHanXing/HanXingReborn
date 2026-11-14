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

package techreborn.space.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Strict parser with JSON-path diagnostics; no registry fallback to air. */
public final class PlanetParser {
	private static final Set<String> TYPES = Set.of("barren", "molten", "toxic", "continental", "ocean",
			"frozen", "gas_giant", "asteroid_belt", "black_hole", "star");
	private final Identifier file;
	private final RegistryOps<JsonElement> ops;

	private PlanetParser(Identifier file, RegistryWrapper.WrapperLookup registries) {
		this.file = file;
		this.ops = RegistryOps.of(JsonOps.INSTANCE, registries);
	}

	public static PlanetDefinition parse(Identifier id, Identifier file, JsonElement json,
			RegistryWrapper.WrapperLookup registries) {
		return new PlanetParser(file, registries).planet(id, json);
	}

	private PlanetDefinition planet(Identifier id, JsonElement json) {
		JsonObject root = object(json, "$", "type", "tier", "system", "from_system", "parent", "sort_order",
				"display", "marker_block", "marker_scale", "time", "physical", "missions", "resources", "visibility", "dimension", "tags");
		String type = string(root, "type", "$");
		if (!TYPES.contains(type)) {
			throw error("$.type", "expected one of " + TYPES + ", got '" + type + "'");
		}
		int tier = tier(root, "tier", "$");
		String system = string(root, "system", "$");
		Identifier fromSystem = identifier(string(root, "from_system", "$"), "$.from_system");
		JsonElement parentValue = required(root, "parent", "$");
		Identifier parent = parentValue.isJsonNull() ? null : identifier(stringValue(parentValue, "$.parent"), "$.parent");
		int sortOrder = root.has("sort_order") ? integer(root, "sort_order", "$") : id.toString().hashCode();
		JsonObject display = object(required(root, "display", "$"), "$.display", "name_key", "description_key");
		PlanetDefinition.Display displayValue = new PlanetDefinition.Display(string(display, "name_key", "$.display"),
				string(display, "description_key", "$.display"));
		Identifier markerId = identifier(string(root, "marker_block", "$"), "$.marker_block");
		if (!Registries.BLOCK.containsId(markerId)) {
			throw error("$.marker_block", "unregistered block '" + markerId + "'");
		}
		Block marker = Registries.BLOCK.get(markerId);
		double markerScale = root.has("marker_scale") ? number(root, "marker_scale", "$") : 1;
		if (markerScale <= 0) {
			throw error("$.marker_scale", "must be positive");
		}
		int time = positive(root, "time", "$");
		PlanetDefinition.Physical physical = physical(required(root, "physical", "$"), parent != null, sortOrder);
		PlanetDefinition.Missions missions = missions(required(root, "missions", "$"), tier);
		List<PlanetDefinition.Resource> resources = resources(root.has("resources") ? root.get("resources") : new JsonArray());
		if (missions.mining().enabled() && resources.isEmpty()) {
			throw error("$.resources", "enabled mining requires at least one resource");
		}
		if (missions.land().enabled() && !missions.survey().enabled()) {
			throw error("$.missions.land.enabled", "requires missions.survey.enabled=true");
		}
		if (missions.outpost().enabled() && !missions.land().enabled()) {
			throw error("$.missions.outpost.enabled", "requires missions.land.enabled=true");
		}
		if (missions.mining().enabled() && missions.mining().requiresLanding() && !missions.land().enabled()) {
			throw error("$.missions.mining.requires_landing", "requires missions.land.enabled=true");
		}
		if (type.equals("star") && (missions.survey().enabled() || missions.land().enabled()
				|| missions.outpost().enabled() || missions.mining().enabled())) {
			throw error("$.missions", "stars are display-only: every mission must be disabled");
		}
		if (type.equals("black_hole") && (missions.land().enabled() || missions.outpost().enabled()
				|| missions.mining().requiresLanding())) {
			throw error("$.missions", "black holes cannot land/build outposts and mining requires_landing must be false");
		}
		PlanetDefinition.Visibility visibility = visibility(root.get("visibility"));
		Identifier dimension = root.has("dimension") ? identifier(string(root, "dimension", "$"), "$.dimension") : null;
		return new PlanetDefinition(id, type, tier, system, fromSystem, parent, sortOrder, displayValue, marker,
				markerScale, time, physical, missions, resources, visibility, dimension,
				root.has("tags") ? strings(root.get("tags"), "$.tags") : List.of());
	}

	private PlanetDefinition.Physical physical(JsonElement element, boolean orbiting, int sortOrder) {
		String path = "$.physical";
		JsonObject object = object(element, path, "radius_km", "orbit_radius_au", "orbit_phase_deg", "temperature_k",
				"rotation_period_hours", "orbital_period_days", "orbit_features");
		double radius = nonnegative(object, "radius_km", path);
		double orbit = object.has("orbit_radius_au") ? nonnegative(object, "orbit_radius_au", path) : 0;
		if (orbiting && orbit <= 0) {
			throw error(path + ".orbit_radius_au", "must be present and >0 for a body with a parent");
		}
		double phase = object.has("orbit_phase_deg") ? number(object, "orbit_phase_deg", path) : Math.floorMod(sortOrder, 360);
		phase = ((phase % 360) + 360) % 360;
		return new PlanetDefinition.Physical(radius, orbit, phase, nonnegative(object, "temperature_k", path),
				number(object, "rotation_period_hours", path), nonnegative(object, "orbital_period_days", path),
				object.has("orbit_features") ? strings(object.get("orbit_features"), path + ".orbit_features") : List.of());
	}

	private PlanetDefinition.Missions missions(JsonElement value, int planetTier) {
		JsonObject missions = object(value, "$.missions", "survey", "land", "outpost", "mining");
		PlanetDefinition.Stage survey = stage(required(missions, "survey", "$.missions"), "$.missions.survey", planetTier);
		PlanetDefinition.Stage land = stage(required(missions, "land", "$.missions"), "$.missions.land", planetTier);
		PlanetDefinition.Stage outpost = stage(required(missions, "outpost", "$.missions"), "$.missions.outpost", planetTier);
		String path = "$.missions.mining";
		JsonObject mining = object(required(missions, "mining", "$.missions"), path, "enabled", "requires_landing", "variants");
		boolean enabled = bool(mining, "enabled", path);
		boolean requiresLanding = !mining.has("requires_landing") || bool(mining, "requires_landing", path);
		return new PlanetDefinition.Missions(survey, land, outpost,
				new PlanetDefinition.Mining(enabled, requiresLanding, variants(mining, path, planetTier, enabled)));
	}

	private PlanetDefinition.Stage stage(JsonElement value, String path, int planetTier) {
		JsonObject stage = object(value, path, "enabled", "time", "variants");
		boolean enabled = bool(stage, "enabled", path);
		int time = stage.has("time") ? positive(stage, "time", path) : 0;
		if (enabled && time == 0) {
			throw error(path + ".time", "enabled stage requires positive duration in ticks");
		}
		return new PlanetDefinition.Stage(enabled, time, variants(stage, path, planetTier, enabled));
	}

	private List<PlanetDefinition.Variant> variants(JsonObject object, String path, int planetTier, boolean enabled) {
		JsonArray array = object.has("variants") ? array(object.get("variants"), path + ".variants") : new JsonArray();
		if (enabled && array.isEmpty()) {
			throw error(path + ".variants", "enabled mission requires at least one explicit equipment-tier variant");
		}
		if (array.size() > 3) {
			throw error(path + ".variants", "at most three equipment tiers are supported");
		}
		Set<Integer> seen = new HashSet<>();
		List<PlanetDefinition.Variant> variants = new ArrayList<>();
		for (int i = 0; i < array.size(); i++) {
			String variantPath = path + ".variants[" + i + "]";
			JsonObject variant = object(array.get(i), variantPath, "tier", "inputs", "outputs");
			int tier = tier(variant, "tier", variantPath);
			if (tier < planetTier || !seen.add(tier)) {
				throw error(variantPath + ".tier", "must be unique and at least the planet tier (" + planetTier + ")");
			}
			boolean mining = path.equals("$.missions.mining");
			variants.add(new PlanetDefinition.Variant(tier, stacks(required(variant, "inputs", variantPath), variantPath + ".inputs", mining ? 4 : 9),
					stacks(required(variant, "outputs", variantPath), variantPath + ".outputs", mining ? 6 : 9)));
		}
		variants.sort(Comparator.comparingInt(PlanetDefinition.Variant::tier));
		return List.copyOf(variants);
	}

	private List<ItemStack> stacks(JsonElement value, String path, int limit) {
		JsonArray array = array(value, path);
		if (array.size() > limit) {
			throw error(path, "at most " + limit + " entries fit this mission inventory; got " + array.size());
		}
		List<ItemStack> stacks = new ArrayList<>();
		for (int i = 0; i < array.size(); i++) {
			String stackPath = path + "[" + i + "]";
			stacks.add(stack(object(array.get(i), stackPath, "item", "count", "components"), stackPath));
		}
		return List.copyOf(stacks);
	}

	private ItemStack stack(JsonObject object, String path) {
		Identifier item = identifier(string(object, "item", path), path + ".item");
		if (!Registries.ITEM.containsId(item) || Registries.ITEM.get(item) == Items.AIR) {
			throw error(path + ".item", "expected registered, non-air item; got '" + item + "'");
		}
		int count = positive(object, "count", path);
		JsonObject vanilla = new JsonObject();
		vanilla.addProperty("id", item.toString());
		// These are aggregate mission amounts, not necessarily one physical stack.
		// Vanilla's count codec caps at 99; decode the full component patch on a
		// unit stack, then preserve the exact positive mission amount separately.
		vanilla.addProperty("count", 1);
		if (object.has("components")) {
			if (!object.get("components").isJsonObject()) {
				throw error(path + ".components", "expected data component object");
			}
			vanilla.add("components", object.get("components").deepCopy());
		}
		var result = ItemStack.CODEC.parse(ops, vanilla);
		if (result.error().isPresent()) {
			throw error(path + (object.has("components") ? ".components" : ".item"), result.error().get().message());
		}
		ItemStack decoded = result.result().orElseThrow(() -> error(path, "item codec produced no value"));
		if (decoded.isEmpty()) {
			throw error(path, "empty stack is not allowed");
		}
		return decoded.copyWithCount(count);
	}

	private List<PlanetDefinition.Resource> resources(JsonElement value) {
		JsonArray array = array(value, "$.resources");
		if (array.size() > 256) {
			throw error("$.resources", "at most 256 resources are allowed");
		}
		Set<String> seen = new HashSet<>();
		List<PlanetDefinition.Resource> resources = new ArrayList<>();
		for (int i = 0; i < array.size(); i++) {
			String path = "$.resources[" + i + "]";
			JsonObject resource = object(array.get(i), path, "id", "item", "count", "components", "order");
			String id = string(resource, "id", path);
			if (!id.matches("[a-z0-9_./-]+") || !seen.add(id)) {
				throw error(path + ".id", "must be a unique stable resource path ([a-z0-9_./-]+)");
			}
			resources.add(new PlanetDefinition.Resource(id, stack(resource, path), resource.has("order") ? integer(resource, "order", path) : i));
		}
		resources.sort(Comparator.comparingInt(PlanetDefinition.Resource::order).thenComparing(PlanetDefinition.Resource::id));
		return List.copyOf(resources);
	}

	private PlanetDefinition.Visibility visibility(JsonElement value) {
		if (value == null) {
			return new PlanetDefinition.Visibility("always", "after_survey", "after_survey");
		}
		JsonObject visibility = object(value, "$.visibility", "name", "physical", "resources");
		return new PlanetDefinition.Visibility(visibilityValue(visibility, "name", "always", false),
				visibilityValue(visibility, "physical", "after_survey", false), visibilityValue(visibility, "resources", "after_survey", true));
	}

	private String visibilityValue(JsonObject object, String key, String fallback, boolean allowLand) {
		String value = object.has(key) ? string(object, key, "$.visibility") : fallback;
		if (!(value.equals("always") || value.equals("after_survey") || (allowLand && value.equals("after_land")))) {
			throw error("$.visibility." + key, "expected always/after_survey" + (allowLand ? "/after_land" : ""));
		}
		return value;
	}

	private List<String> strings(JsonElement value, String path) {
		JsonArray array = array(value, path);
		if (array.size() > 256) {
			throw error(path, "at most 256 values are allowed");
		}
		List<String> strings = new ArrayList<>();
		for (int i = 0; i < array.size(); i++) {
			strings.add(stringValue(array.get(i), path + "[" + i + "]"));
		}
		return List.copyOf(strings);
	}

	private JsonObject object(JsonElement element, String path, String... keys) {
		if (element == null || !element.isJsonObject()) {
			throw error(path, "expected object");
		}
		JsonObject object = element.getAsJsonObject();
		Set<String> allowed = Set.of(keys);
		for (String key : object.keySet()) {
			if (!allowed.contains(key)) {
				throw error(path + "." + key, "unknown field (planet ID comes from its file path; legacy multipliers/random fields are unsupported)");
			}
		}
		return object;
	}

	private JsonArray array(JsonElement value, String path) {
		if (value == null || !value.isJsonArray()) {
			throw error(path, "expected array");
		}
		return value.getAsJsonArray();
	}

	private JsonElement required(JsonObject object, String key, String path) {
		if (!object.has(key)) {
			throw error(path + "." + key, "required field is missing");
		}
		return object.get(key);
	}

	private String string(JsonObject object, String key, String path) {
		return stringValue(required(object, key, path), path + "." + key);
	}

	private String stringValue(JsonElement value, String path) {
		if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || value.getAsString().isBlank()) {
			throw error(path, "expected nonblank string");
		}
		return value.getAsString();
	}

	private boolean bool(JsonObject object, String key, String path) {
		JsonElement value = required(object, key, path);
		if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
			throw error(path + "." + key, "expected boolean");
		}
		return value.getAsBoolean();
	}

	private int integer(JsonObject object, String key, String path) {
		JsonElement value = required(object, key, path);
		try {
			if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
				throw new ArithmeticException();
			}
			return value.getAsBigDecimal().intValueExact();
		} catch (ArithmeticException | NumberFormatException exception) {
			throw error(path + "." + key, "expected exact 32-bit integer");
		}
	}

	private int positive(JsonObject object, String key, String path) {
		int value = integer(object, key, path);
		if (value <= 0) {
			throw error(path + "." + key, "must be positive");
		}
		return value;
	}

	private int tier(JsonObject object, String key, String path) {
		int value = integer(object, key, path);
		if (value < 1 || value > 3) {
			throw error(path + "." + key, "expected tier 1, 2 or 3");
		}
		return value;
	}

	private double number(JsonObject object, String key, String path) {
		JsonElement value = required(object, key, path);
		if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
			throw error(path + "." + key, "expected finite number");
		}
		double number = value.getAsDouble();
		if (!Double.isFinite(number)) {
			throw error(path + "." + key, "expected finite number");
		}
		return number;
	}

	private double nonnegative(JsonObject object, String key, String path) {
		double value = number(object, key, path);
		if (value < 0) {
			throw error(path + "." + key, "must not be negative");
		}
		return value;
	}

	private Identifier identifier(String value, String path) {
		Identifier id = Identifier.tryParse(value);
		if (id == null || !value.contains(":")) {
			throw error(path, "expected namespaced identifier, got '" + value + "'");
		}
		return id;
	}

	private IllegalArgumentException error(String path, String message) {
		return new IllegalArgumentException(file + " " + path + ": " + message);
	}
}
