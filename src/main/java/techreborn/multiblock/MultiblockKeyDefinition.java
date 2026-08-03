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
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import reborncore.common.blockentity.MultiblockWriter.PatternBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * Represents a single key entry inside a JSON multiblock definition.
 * <p>
 * A key maps a pattern character to both a {@link BiPredicate} (used to validate
 * the block at a position) and a {@link BlockState} (used by the hologram renderer).
 * <p>
 * Supported JSON fields (all optional, at least one match source is expected):
 * <ul>
 *     <li>{@code "block": "modid:block_id"} - a single block, matched by type</li>
 *     <li>{@code "blocks": ["modid:a", "modid:b"]} - any block from the list</li>
 *     <li>{@code "tag": "modid:tag_id"} - any block carrying the given block tag</li>
 *     <li>{@code "not": "modid:block_id"} - the position must NOT be this block</li>
 *     <li>{@code "air": true} - the position must be air</li>
 *     <li>{@code "any": true} - any block is accepted</li>
 *     <li>{@code "hologram": "modid:block_id"} - optional override for the hologram block state</li>
 * </ul>
 * If no match source is given the key defaults to {@code "any"}.
 */
public class MultiblockKeyDefinition {

	private final BiPredicate<BlockView, BlockPos> predicate;
	private final BlockState hologramState;

	private MultiblockKeyDefinition(BiPredicate<BlockView, BlockPos> predicate, BlockState hologramState) {
		this.predicate = predicate;
		this.hologramState = hologramState;
	}

	public BiPredicate<BlockView, BlockPos> getPredicate() {
		return predicate;
	}

	public BlockState getHologramState() {
		return hologramState;
	}

	public PatternBlock toPatternBlock() {
		return new PatternBlock(predicate, hologramState);
	}

	/**
	 * Parses a key definition from its JSON form.
	 *
	 * @param json {@link JsonObject} the key definition object
	 * @return {@link MultiblockKeyDefinition} the parsed definition
	 */
	public static MultiblockKeyDefinition parse(JsonObject json) {
		BiPredicate<BlockView, BlockPos> predicate = null;
		BlockState hologramState = Blocks.AIR.getDefaultState();

		if (json.has("air") && json.get("air").getAsBoolean()) {
			predicate = (view, pos) -> view.getBlockState(pos).isAir();
		} else if (json.has("any") && json.get("any").getAsBoolean()) {
			predicate = (view, pos) -> true;
		}

		if (json.has("block")) {
			Block block = resolveBlock(json.get("block").getAsString());
			predicate = (view, pos) -> view.getBlockState(pos).isOf(block);
			hologramState = block.getDefaultState();
		} else if (json.has("blocks")) {
			List<Block> blocks = new ArrayList<>();
			JsonArray array = json.getAsJsonArray("blocks");
			array.forEach(element -> blocks.add(resolveBlock(element.getAsString())));
			predicate = (view, pos) -> {
				Block block = view.getBlockState(pos).getBlock();
				return blocks.contains(block);
			};
			hologramState = blocks.isEmpty() ? Blocks.AIR.getDefaultState() : blocks.get(0).getDefaultState();
		} else if (json.has("tag")) {
			TagKey<Block> tag = TagKey.of(RegistryKeys.BLOCK, Identifier.of(json.get("tag").getAsString()));
			predicate = (view, pos) -> view.getBlockState(pos).isIn(tag);
			hologramState = firstBlockFromTag(tag);
		} else if (json.has("not")) {
			Block block = resolveBlock(json.get("not").getAsString());
			predicate = (view, pos) -> !view.getBlockState(pos).isOf(block);
			// The hole is usually empty; default to air unless overridden
			hologramState = Blocks.AIR.getDefaultState();
		}

		if (predicate == null) {
			// No explicit match source: accept anything, but keep the hologram block
			predicate = (view, pos) -> true;
		}

		if (json.has("hologram")) {
			hologramState = resolveBlock(json.get("hologram").getAsString()).getDefaultState();
		}

		return new MultiblockKeyDefinition(predicate, hologramState);
	}

	private static Block resolveBlock(String id) {
		Identifier identifier = Identifier.of(id);
		if (Registries.BLOCK.containsId(identifier)) {
			return Registries.BLOCK.get(identifier);
		}
		throw new IllegalArgumentException("Unknown block id: " + id);
	}

	private static BlockState firstBlockFromTag(TagKey<Block> tag) {
		return Registries.BLOCK.getEntryList(tag)
				.flatMap(entries -> entries.stream().findFirst())
				.map(entry -> entry.value().getDefaultState())
				.orElseGet(Blocks.AIR::getDefaultState);
	}
}
