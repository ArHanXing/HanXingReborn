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

package techreborn.multiblock;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import techreborn.TechReborn;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Loads {@link MultiblockDefinition}s from the config directory
 * {@code config/techreborn/multiblock/}. Files placed there are matched to a
 * machine by their file name (e.g. {@code large_chemical_reactor.json} matches
 * a machine whose {@code getMultiblockId()} returns {@code large_chemical_reactor}).
 * <p>
 * Default definitions shipped with the mod are loaded from the jar resources
 * ({@code assets/techreborn/multiblock/}) whenever no user override exists,
 * so players can freely override or extend any structure through the config folder.
 */
public class MultiblockDefinitionLoader {

	private static final String CONFIG_SUB_DIR = "techreborn/multiblock";
	private static final String DEFAULT_RESOURCE_DIR = "assets/techreborn/multiblock/";

	private static final Map<String, MultiblockDefinition> definitions = new HashMap<>();

	/**
	 * Default definitions bundled with the mod. When a file with the same name
	 * exists in the config directory, the config version takes priority.
	 */
	private static final String[] DEFAULT_DEFINITIONS = {
			"large_chemical_reactor",
			"distillation_tower",
			"fluid_replicator",
			"implosion_compressor",
			"industrial_blast_furnace",
			"industrial_grinder",
			"industrial_sawmill",
			"vacuum_freezer",
			"rotary_hearth_furnace",
			"large_compressor",
			"large_wire_mill",
			"large_ore_crusher",
			"large_grinder",
			"primitive_distillation_tower",
			"large_lathe",
			"furnace_pro_max",
			"precise_assembler"
	};

	private MultiblockDefinitionLoader() {
	}

	/**
	 * Loads (or reloads) all multiblock definitions. Called during mod init,
	 * after all blocks have been registered.
	 */
	public static void init() {
		definitions.clear();

		loadFromConfigDir();
		loadDefaults();

		TechReborn.LOGGER.info("Loaded {} JSON multiblock definition(s): {}", definitions.size(), definitions.keySet());
	}

	/**
	 * Returns the definition for the given machine id, or {@code null} if
	 * no definition (or only an invalid one) is available.
	 *
	 * @param id {@link String} the machine id
	 * @return {@link MultiblockDefinition} the definition, or {@code null}
	 */
	public static MultiblockDefinition get(String id) {
		return definitions.get(id);
	}

	public static boolean has(String id) {
		return definitions.containsKey(id);
	}

	/**
	 * Returns all loaded definitions (config overrides already applied).
	 * Used by client-side displays (e.g. the EMI multiblock info page) to
	 * enumerate every available structure.
	 *
	 * @return {@link Map} an unmodifiable map of definition id to definition
	 */
	public static Map<String, MultiblockDefinition> getAll() {
		return Collections.unmodifiableMap(definitions);
	}

	private static void loadFromConfigDir() {
		Path configDir = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_SUB_DIR);
		if (!Files.isDirectory(configDir)) {
			return;
		}
		try (Stream<Path> paths = Files.list(configDir)) {
			paths.filter(path -> path.getFileName().toString().endsWith(".json"))
					.forEach(MultiblockDefinitionLoader::loadFromFile);
		} catch (IOException e) {
			TechReborn.LOGGER.warn("Failed to list multiblock config directory {}", configDir, e);
		}
	}

	private static void loadDefaults() {
		for (String id : DEFAULT_DEFINITIONS) {
			if (!definitions.containsKey(id)) {
				loadFromResource(id);
			}
		}
	}

	private static void loadFromFile(Path file) {
		String id = file.getFileName().toString().replace(".json", "");
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
			register(id, json);
		} catch (IOException | JsonParseException | IllegalArgumentException e) {
			TechReborn.LOGGER.warn("Failed to load multiblock definition {}: {}", id, e.getMessage());
		}
	}

	private static void loadFromResource(String id) {
		try (InputStream stream = MultiblockDefinitionLoader.class.getClassLoader()
				.getResourceAsStream(DEFAULT_RESOURCE_DIR + id + ".json")) {
			if (stream == null) {
				TechReborn.LOGGER.warn("No default multiblock definition found for {}", id);
				return;
			}
			try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
				JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
				register(id, json);
			}
		} catch (IOException | JsonParseException | IllegalArgumentException e) {
			TechReborn.LOGGER.warn("Failed to load default multiblock definition {}: {}", id, e.getMessage());
		}
	}

	private static void register(String id, JsonObject json) {
		MultiblockDefinition definition = MultiblockDefinition.parse(json);
		definitions.put(id, definition);
		TechReborn.LOGGER.debug("Registered multiblock definition '{}'", id);
	}
}
