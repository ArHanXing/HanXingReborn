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

package techreborn.client.multiblock;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import techreborn.TechReborn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Client-side state and logic for the multiblock structure recording tool
 * ({@code techreborn:multiblock_selector}).
 * <p>
 * Usage:
 * <ul>
 *     <li><b>Right-click a block</b> - select / deselect the block (single mode)</li>
 *     <li><b>Right-click air</b> - toggle between single and rectangular selection mode
 *         (the selection is kept across mode switches)</li>
 *     <li><b>Shift + right-click a block</b> - use that block as the machine controller
 *         position, generate the matching JSON definition and print it to chat + log</li>
 *     <li><b>Shift + left-click air</b> - clear the current selection</li>
 * </ul>
 * Selected blocks are highlighted with a red outline on the client.
 */
public class MultiblockSelector {

	public static final MultiblockSelector INSTANCE = new MultiblockSelector();

	private static final Logger LOGGER = LoggerFactory.getLogger(TechReborn.MOD_ID);

	private static final String[] KEYS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".split("");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	// Shared selection that survives mode switches
	private final Set<BlockPos> selected = new LinkedHashSet<>();
	// Rectangular mode corner state
	private BlockPos rectCorner1;
	private BlockPos rectCorner2;
	private boolean rectMode = false;

	private MultiblockSelector() {
	}

	/**
	 * @return {@code true} if rectangular selection mode is active
	 */
	public boolean isRectMode() {
		return rectMode;
	}

	/**
	 * @return the currently selected positions (combined across both modes)
	 */
	public List<BlockPos> getSelectedPositions() {
		return new ArrayList<>(selected);
	}

	/**
	 * Handles a right-click on a block while holding the selector tool.
	 *
	 * @param player {@link PlayerEntity} the clicking player
	 * @param world  {@link World} the client world
	 * @param pos    {@link BlockPos} the clicked block position
	 */
	public void onUseBlock(PlayerEntity player, World world, BlockPos pos) {
		if (player.isSneaking()) {
			outputJson(player, world, pos);
			return;
		}

		if (rectMode) {
			if (rectCorner1 == null) {
				rectCorner1 = pos;
				sendMessage(player, "矩形模式：已选择角点 1 §e" + pos + "§r，请右键选择角点 2");
			} else if (rectCorner2 == null) {
				rectCorner2 = pos;
				toggleRectRegion(player);
				sendMessage(player, "矩形模式：已选择角点 2 §e" + pos + "§r，当前共选中 §e" + selected.size() + "§r 个方块");
			} else {
				// Start a new rectangle
				rectCorner1 = pos;
				rectCorner2 = null;
				sendMessage(player, "矩形模式：已重置，新的角点 1 §e" + pos + "§r");
			}
			return;
		}

		if (selected.contains(pos)) {
			selected.remove(pos);
			sendMessage(player, "已取消选择 §e" + pos + "§r（剩余 " + selected.size() + " 个）");
		} else {
			selected.add(pos);
			sendMessage(player, "已选择 §e" + pos + "§r（共 " + selected.size() + " 个）");
		}
	}

	/**
	 * Toggles every block inside the current rectangle region in/out of the
	 * shared selection. Used when the second corner is set.
	 */
	private void toggleRectRegion(PlayerEntity player) {
		int added = 0;
		int removed = 0;
		for (int x = Math.min(rectCorner1.getX(), rectCorner2.getX()); x <= Math.max(rectCorner1.getX(), rectCorner2.getX()); x++) {
			for (int y = Math.min(rectCorner1.getY(), rectCorner2.getY()); y <= Math.max(rectCorner1.getY(), rectCorner2.getY()); y++) {
				for (int z = Math.min(rectCorner1.getZ(), rectCorner2.getZ()); z <= Math.max(rectCorner1.getZ(), rectCorner2.getZ()); z++) {
					BlockPos p = new BlockPos(x, y, z);
					if (!selected.add(p)) {
						selected.remove(p);
						removed++;
					} else {
						added++;
					}
				}
			}
		}
		if (added > 0 && removed > 0) {
			sendMessage(player, "矩形区域：新增 §e" + added + "§r 个，取消 §e" + removed + "§r 个");
		} else if (added > 0) {
			sendMessage(player, "矩形区域：新增 §e" + added + "§r 个方块");
		} else if (removed > 0) {
			sendMessage(player, "矩形区域：取消 §e" + removed + "§r 个方块");
		}
	}

	/**
	 * Handles a right-click on air (no block hit) while holding the selector
	 * tool: toggles between single and rectangular selection mode.
	 * The current selection is kept across mode switches.
	 */
	public void onUseItem(PlayerEntity player) {
		rectMode = !rectMode;
		rectCorner1 = null;
		rectCorner2 = null;
		if (rectMode) {
			sendMessage(player, "已切换为 §b矩形选择模式§r（右键两次选择对角区域，选区保留，当前 " + selected.size() + " 个）");
		} else {
			sendMessage(player, "已切换为 §b单点选择模式§r（右键选择/取消，Shift+右键输出 JSON，当前 " + selected.size() + " 个）");
		}
	}

	/**
	 * Clears the current selection.
	 *
	 * @param player {@link PlayerEntity} the player (for chat feedback)
	 */
	public void clearSelection(PlayerEntity player) {
		selected.clear();
		rectCorner1 = null;
		rectCorner2 = null;
		sendMessage(player, "已清空选择");
	}

	/**
	 * Generates the JSON multiblock definition for the current selection,
	 * using {@code controller} as the machine controller position (determines
	 * the {@code translate} offset). The JSON is printed to the log and chat.
	 *
	 * @param player     {@link PlayerEntity} the player (for chat output)
	 * @param world      {@link World} the client world
	 * @param controller {@link BlockPos} the controller block position
	 */
	public void outputJson(PlayerEntity player, World world, BlockPos controller) {
		List<BlockPos> positions = getSelectedPositions();
		if (positions.isEmpty()) {
			sendMessage(player, "§c没有选中任何方块，无法生成 JSON§r");
			return;
		}

		String json = generateJson(world, controller, positions);
		LOGGER.info("Multiblock JSON (controller {}) :\n{}", controller, json);

		// Copyable button + readable JSON in chat
		sendMessage(player, "§6[Multiblock Selector]§r 已生成 JSON（控制器 §e" + controller + "§r，已清空选择）：");
		sendMessage(player, Text.literal("§6[§n点击复制完整 JSON§r§6]§r")
				.styled(style -> style
						.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, json))
						.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("点击复制 JSON 到剪贴板")))));
		for (String line : json.split("\n")) {
			sendMessage(player, line);
		}
		clear();
	}

	private void clear() {
		selected.clear();
		rectCorner1 = null;
		rectCorner2 = null;
	}

	private void sendMessage(PlayerEntity player, String message) {
		player.sendMessage(Text.literal(message), false);
	}

	private void sendMessage(PlayerEntity player, Text message) {
		player.sendMessage(message, false);
	}

	private String generateJson(World world, BlockPos controller, List<BlockPos> positions) {
		// Bounding box of the selection
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		for (BlockPos pos : positions) {
			minX = Math.min(minX, pos.getX());
			minY = Math.min(minY, pos.getY());
			minZ = Math.min(minZ, pos.getZ());
			maxX = Math.max(maxX, pos.getX());
			maxY = Math.max(maxY, pos.getY());
			maxZ = Math.max(maxZ, pos.getZ());
		}

		int translateX = minX - controller.getX();
		int translateY = minY - controller.getY();
		int translateZ = minZ - controller.getZ();

		// Assign a key character to each distinct block type
		Map<Block, Character> blockToKey = new HashMap<>();
		int keyIndex = 0;
		for (BlockPos pos : positions) {
			Block block = world.getBlockState(pos).getBlock();
			if (block == Blocks.AIR) {
				continue; // Air positions are skipped (space) in the pattern
			}
			if (!blockToKey.containsKey(block)) {
				if (keyIndex >= KEYS.length) {
					throw new IllegalStateException("Too many distinct block types (" + keyIndex + "), cannot assign more keys");
				}
				blockToKey.put(block, KEYS[keyIndex++].charAt(0));
			}
		}

		// Build the 3D pattern: layers[y][z][x]
		JsonArray layersJson = new JsonArray();
		for (int y = minY; y <= maxY; y++) {
			JsonArray rows = new JsonArray();
			for (int z = minZ; z <= maxZ; z++) {
				StringBuilder row = new StringBuilder();
				for (int x = minX; x <= maxX; x++) {
					BlockPos pos = new BlockPos(x, y, z);
					if (!positions.contains(pos)) {
						row.append(' ');
						continue;
					}
					Block block = world.getBlockState(pos).getBlock();
					if (block == Blocks.AIR) {
						row.append(' ');
					} else {
						row.append(blockToKey.get(block));
					}
				}
				rows.add(row.toString());
			}
			layersJson.add(rows);
		}

		// Keys: character -> block id
		JsonObject keysJson = new JsonObject();
		blockToKey.forEach((block, key) -> {
			JsonObject keyJson = new JsonObject();
			keyJson.addProperty("block", Registries.BLOCK.getId(block).toString());
			keysJson.add(String.valueOf(key), keyJson);
		});

		JsonObject json = new JsonObject();
		JsonArray translate = new JsonArray();
		translate.add(translateX);
		translate.add(translateY);
		translate.add(translateZ);
		json.add("translate", translate);
		json.add("layers", layersJson);
		json.add("keys", keysJson);

		return GSON.toJson(json);
	}
}
