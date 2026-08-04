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

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import reborncore.common.blockentity.MultiblockWriter;
import techreborn.blocks.misc.BlockCoil;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * Generic coil scanning for JSON-driven multiblock machines.
 * <p>
 * Instead of hard-coding where the coils live (e.g. the IBF's "bottom center
 * casing" assumption), this enumerates every block position of the machine's
 * JSON structure (applying the same translation and rotation as validation)
 * and scans them for {@link BlockCoil}s. Only one coil type may be used;
 * mixed coils invalidate the machine.
 */
public final class CoilHeatScanner {

	private CoilHeatScanner() {
	}

	/**
	 * Scans the structure for coils and returns the heat of the (single) coil
	 * type used.
	 *
	 * @param world         {@link World} the world
	 * @param controllerPos {@link BlockPos} the controller block position
	 * @param facing        {@link Direction} the machine facing
	 * @param multiblockId  {@link String} the JSON definition id
	 * @return the coil heat; {@code 0} if no coils or no definition;
	 *         {@code -1} if mixed coil types are used (invalid structure)
	 */
	public static int scanCoilHeat(World world, BlockPos controllerPos, Direction facing, String multiblockId) {
		if (world == null) {
			return -1;
		}
		Block foundCoilBlock = null;
		for (BlockPos position : collectPositions(controllerPos, facing, multiblockId)) {
			Block block = world.getBlockState(position).getBlock();
			if (block instanceof BlockCoil) {
				if (foundCoilBlock == null) {
					foundCoilBlock = block;
				} else if (foundCoilBlock != block) {
					// Mixed coils -> reject
					return -1;
				}
			}
		}
		return foundCoilBlock instanceof BlockCoil coil ? coil.heat : 0;
	}

	/**
	 * Collects the world positions of every block in the machine's JSON
	 * structure, applying the same translation and rotation as multiblock
	 * validation. Empty if no definition exists.
	 *
	 * @param controllerPos {@link BlockPos} the controller block position
	 * @param facing        {@link Direction} the machine facing
	 * @param multiblockId  {@link String} the JSON definition id
	 * @return {@link List} of world positions
	 */
	public static List<BlockPos> collectPositions(BlockPos controllerPos, Direction facing, String multiblockId) {
		MultiblockDefinition definition = MultiblockDefinitionLoader.get(multiblockId);
		if (definition == null) {
			return List.of();
		}
		PositionCollector collector = new PositionCollector(controllerPos);
		definition.apply(collector.rotate(facing.getOpposite()));
		return collector.positions;
	}

	/**
	 * Collects the world positions of every block written by a multiblock
	 * definition.
	 */
	private static final class PositionCollector implements MultiblockWriter {

		private final BlockPos origin;
		private final List<BlockPos> positions = new ArrayList<>();

		PositionCollector(BlockPos origin) {
			this.origin = origin;
		}

		@Override
		public MultiblockWriter add(int x, int y, int z, BiPredicate<BlockView, BlockPos> predicate, BlockState state) {
			positions.add(origin.add(x, y, z));
			return this;
		}
	}
}
