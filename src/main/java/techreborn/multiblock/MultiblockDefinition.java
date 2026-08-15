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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import reborncore.common.blockentity.MultiblockWriter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * A JSON-driven definition of a custom multiblock structure.
 * <p>
 * The definition is composed of:
 * <ul>
 *     <li>{@code translate}: [x, y, z] offset of the pattern origin relative to the controller block</li>
 *     <li>{@code layers}: a 3D pattern of characters, indexed bottom to top</li>
 *     <li>{@code keys}: a map from pattern characters to {@link MultiblockKeyDefinition}s</li>
 * </ul>
 * The structure can be written to any {@link MultiblockWriter} for either
 * validation or hologram rendering.
 */
public class MultiblockDefinition {

	private final int translateX;
	private final int translateY;
	private final int translateZ;
	private final List<List<String>> layers;
	private final Map<Character, MultiblockKeyDefinition> keys;

	private MultiblockDefinition(int translateX, int translateY, int translateZ, List<List<String>> layers,
			Map<Character, MultiblockKeyDefinition> keys) {
		this.translateX = translateX;
		this.translateY = translateY;
		this.translateZ = translateZ;
		this.layers = layers;
		this.keys = keys;
	}

	public int getTranslateX() {
		return translateX;
	}

	public int getTranslateY() {
		return translateY;
	}

	public int getTranslateZ() {
		return translateZ;
	}

	public List<List<String>> getLayers() {
		return layers;
	}

	public Map<Character, MultiblockKeyDefinition> getKeys() {
		return keys;
	}

	/**
	 * Writes this definition into the given writer, applying the translation
	 * and the 3D pattern.
	 *
	 * @param writer {@link MultiblockWriter} the target writer
	 */
	public void apply(MultiblockWriter writer) {
		writer.translate(translateX, translateY, translateZ)
				.pattern(layers, key -> keys.get(key).toPatternBlock());
	}

	/**
	 * Iterates every non-empty pattern position as a world-space position with
	 * its full key definition, applying the same translation and rotation as
	 * {@link #apply(MultiblockWriter)} used with
	 * {@code writer.rotate(direction)}. This lets tools (e.g. the multiblock
	 * builder) access the whole key (all candidate blocks) instead of only the
	 * first candidate's hologram state.
	 *
	 * @param origin   {@link BlockPos} the controller block position
	 * @param rotation {@link Direction} the rotation to apply (same value used
	 *                 for structure validation, e.g. {@code facing.getOpposite()})
	 * @param consumer {@link BiConsumer} receives the world position and key
	 */
	public void forEachKey(BlockPos origin, Direction rotation, BiConsumer<BlockPos, MultiblockKeyDefinition> consumer) {
		int rotations = switch (rotation) {
			case NORTH -> 3;
			case WEST -> 2;
			case SOUTH -> 1;
			default -> 0; // EAST
		};
		for (int y = 0; y < layers.size(); y++) {
			List<String> layer = layers.get(y);
			for (int z = 0; z < layer.size(); z++) {
				String row = layer.get(z);
				for (int x = 0; x < row.length(); x++) {
					char c = row.charAt(x);
					if (c == ' ') {
						continue;
					}
					MultiblockKeyDefinition key = keys.get(c);
					if (key == null) {
						continue;
					}
					// Translate first, then rotate: matches the writer chain
					// rotate(translate(collector)).pattern(...).
					int ox = x + translateX;
					int oy = y + translateY;
					int oz = z + translateZ;
					for (int i = 0; i < rotations; i++) {
						int tmp = ox;
						// rotate(): (x, y, z) -> (-z, y, x)
						ox = -oz;
						oz = tmp;
					}
					consumer.accept(origin.add(ox, oy, oz), key);
				}
			}
		}
	}

	/**
	 * Parses a multiblock definition from its JSON form.
	 *
	 * @param json {@link JsonObject} the definition object
	 * @return {@link MultiblockDefinition} the parsed definition
	 */
	public static MultiblockDefinition parse(JsonObject json) {
		int translateX = 0;
		int translateY = 0;
		int translateZ = 0;
		if (json.has("translate") && json.get("translate").isJsonArray()) {
			JsonArray translate = json.getAsJsonArray("translate");
			translateX = translate.get(0).getAsInt();
			translateY = translate.get(1).getAsInt();
			translateZ = translate.get(2).getAsInt();
		}

		// layers: array of layers, bottom (index 0) to top
		List<List<String>> layers = new ArrayList<>();
		JsonArray layersJson = json.getAsJsonArray("layers");
		int rowLength = -1;
		for (int y = 0; y < layersJson.size(); y++) {
			JsonArray layerJson = layersJson.get(y).getAsJsonArray();
			List<String> rows = new ArrayList<>();
			for (int z = 0; z < layerJson.size(); z++) {
				String row = layerJson.get(z).getAsString();
				if (rowLength == -1) {
					rowLength = row.length();
				} else if (row.length() != rowLength) {
					throw new IllegalArgumentException("All rows in a multiblock layer must have the same length");
				}
				rows.add(row);
			}
			layers.add(rows);
		}

		// keys: character -> key definition
		Map<Character, MultiblockKeyDefinition> keys = new HashMap<>();
		JsonObject keysJson = json.getAsJsonObject("keys");
		keysJson.entrySet().forEach(entry -> {
			char key = entry.getKey().charAt(0);
			keys.put(key, MultiblockKeyDefinition.parse(entry.getValue().getAsJsonObject()));
		});

		validateKeys(layers, keys);

		return new MultiblockDefinition(translateX, translateY, translateZ, layers, keys);
	}

	private static void validateKeys(List<List<String>> layers, Map<Character, MultiblockKeyDefinition> keys) {
		for (List<String> rows : layers) {
			for (String row : rows) {
				for (int i = 0; i < row.length(); i++) {
					char c = row.charAt(i);
					if (c != ' ' && !keys.containsKey(c)) {
						throw new IllegalArgumentException("Pattern uses undefined key: '" + c + "'");
					}
				}
			}
		}
	}
}
