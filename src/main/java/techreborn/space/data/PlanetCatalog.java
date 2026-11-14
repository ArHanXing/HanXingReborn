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

import com.google.gson.JsonParser;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import techreborn.TechReborn;

import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Server-thread catalogue with transactional publication. The parent integration
 * calls reload on SERVER_STARTED and a successful END_DATA_PACK_RELOAD, after
 * dynamic registries exist, and clear on SERVER_STOPPED. No mission runs here.
 */
public final class PlanetCatalog {
	public static final int MAX_PLANETS = 256;
	public static final String DIRECTORY = "techreborn/planet";
	private record Snapshot(Map<Identifier, PlanetDefinition> definitions, long revision) {
	}

	private static volatile Snapshot current = new Snapshot(Map.of(), 0);

	private PlanetCatalog() {
	}

	public static Map<Identifier, PlanetDefinition> definitions() {
		return current.definitions();
	}

	@Nullable
	public static PlanetDefinition get(Identifier id) {
		return current.definitions().get(id);
	}

	public static long revision() {
		return current.revision();
	}

	public static boolean reload(ResourceManager manager, RegistryWrapper.WrapperLookup registries) {
		return reload(manager, registries, () -> {
		});
	}

	/**
	 * Validate everything before invoking beforePublish; invalid data never
	 * cancels tasks or changes revision. The callback is the integration's hook
	 * for cancelling/refunding old snapshots and must not throw or re-enter this
	 * catalogue. There is deliberately no task/refund implementation here.
	 *
	 * A callback exception also retains the previous catalogue, but its external
	 * side effects cannot be rolled back; the caller owns callback atomicity.
	 */
	public static synchronized boolean reload(ResourceManager manager, RegistryWrapper.WrapperLookup registries,
			Runnable beforePublish) {
		try {
			Map<Identifier, net.minecraft.resource.Resource> files = manager.findResources(DIRECTORY,
					file -> file.getPath().endsWith(".json"));
			if (files.size() > MAX_PLANETS) {
				throw new IllegalArgumentException("data/*/" + DIRECTORY + ": at most " + MAX_PLANETS + " bodies are allowed, got " + files.size());
			}
			Map<Identifier, PlanetDefinition> parsed = new LinkedHashMap<>();
			// findResources resolves datapack priorities; sort only for deterministic errors.
			for (var entry : files.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
				Identifier file = entry.getKey();
				Identifier id = idFromResource(file);
				try (Reader reader = entry.getValue().getReader()) {
					PlanetDefinition definition = PlanetParser.parse(id, file, JsonParser.parseReader(reader), registries);
					if (parsed.putIfAbsent(id, definition) != null) {
						throw new IllegalArgumentException(file + " $: duplicate celestial ID " + id);
					}
				} catch (IOException | RuntimeException exception) {
					throw new IllegalArgumentException(file + " [pack " + entry.getValue().getPackId() + "]: " + exception.getMessage(), exception);
				}
			}
			validateGraph(parsed);
			Map<Identifier, PlanetDefinition> sorted = new LinkedHashMap<>();
			parsed.values().stream().sorted(Comparator.comparing(PlanetDefinition::system)
					.thenComparingInt(PlanetDefinition::sortOrder).thenComparing(PlanetDefinition::id))
					.forEach(body -> sorted.put(body.id(), body));
			Map<Identifier, PlanetDefinition> immutable = Collections.unmodifiableMap(sorted);
			beforePublish.run();
			current = new Snapshot(immutable, current.revision() + 1);
			TechReborn.LOGGER.info("Loaded {} space planet definition(s), catalogue revision {}", sorted.size(), current.revision());
			return true;
		} catch (RuntimeException exception) {
			TechReborn.LOGGER.error("Rejected space planet catalogue; keeping revision {}: {}", current.revision(), exception.getMessage());
			return false;
		}
	}

	/** Derive sol:moon from sol:techreborn/planet/moon.json (nested paths retained). */
	public static Identifier idFromResource(Identifier file) {
		String path = file.getPath();
		String prefix = DIRECTORY + "/";
		if (!path.startsWith(prefix) || !path.endsWith(".json") || path.length() <= prefix.length() + 5) {
			throw new IllegalArgumentException(file + ": expected " + prefix + "<path>.json");
		}
		return Identifier.of(file.getNamespace(), path.substring(prefix.length(), path.length() - 5));
	}

	/** Public for offline validation and server tests; no publication or mutation. */
	public static void validateGraph(Map<Identifier, PlanetDefinition> definitions) {
		if (definitions.size() > MAX_PLANETS) {
			throw new IllegalArgumentException("Planet catalogue exceeds " + MAX_PLANETS + " bodies");
		}
		for (PlanetDefinition body : definitions.values()) {
			if (body.parent() != null && !definitions.containsKey(body.parent())) {
				throw graphError(body, "parent", "unknown body '" + body.parent() + "'");
			}
			PlanetDefinition star = definitions.get(body.fromSystem());
			if (star == null) {
				throw graphError(body, "from_system", "unknown body '" + body.fromSystem() + "'");
			}
			if (!(star.type().equals("star") || (body.type().equals("black_hole") && body.id().equals(star.id())))) {
				throw graphError(body, "from_system", "must reference a star, or itself for a standalone black hole");
			}
		}
		Map<Identifier, Integer> state = new HashMap<>();
		for (PlanetDefinition body : definitions.values()) {
			visit(body, definitions, state);
		}
	}

	private static void visit(PlanetDefinition body, Map<Identifier, PlanetDefinition> definitions, Map<Identifier, Integer> state) {
		int color = state.getOrDefault(body.id(), 0);
		if (color == 1) {
			throw graphError(body, "parent", "orbital parent cycle involving '" + body.id() + "'");
		}
		if (color == 2) {
			return;
		}
		state.put(body.id(), 1);
		if (body.parent() != null) {
			visit(definitions.get(body.parent()), definitions, state);
		}
		state.put(body.id(), 2);
	}

	private static IllegalArgumentException graphError(PlanetDefinition body, String field, String message) {
		return new IllegalArgumentException("data/" + body.id().getNamespace() + "/" + DIRECTORY + "/"
				+ body.id().getPath() + ".json $." + field + ": " + message);
	}

	/** Release static data between integrated/dedicated server sessions. */
	public static synchronized void clear() {
		current = new Snapshot(Map.of(), 0);
	}
}
