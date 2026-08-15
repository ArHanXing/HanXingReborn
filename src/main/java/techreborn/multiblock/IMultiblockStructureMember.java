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

package techreborn.multiblock;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * Implemented by block entities whose JSON multiblock structure is tracked by
 * {@link MultiblockStructureTracker} so block changes inside the structure can
 * invalidate the cached validity immediately.
 * <p>
 * Both the standard machine hierarchy
 * ({@code JsonMultiblockMachineBlockEntity}) and the generator hierarchy
 * ({@code LargeFluidGeneratorBlockEntity}) implement this.
 */
public interface IMultiblockStructureMember {

	World getWorld();

	BlockPos getPos();

	Direction getFacing();

	/**
	 * @return {@link String} the JSON multiblock definition id
	 */
	String getMultiblockId();

	/**
	 * @param pos {@link BlockPos} the world position to test
	 * @return {@code true} if the position is inside the last verified
	 *         structure bounding box
	 */
	boolean isPositionInStructure(BlockPos pos);

	/**
	 * Clears the cached validation result so the next
	 * {@code isMultiblockValid()} call re-verifies the structure.
	 */
	void invalidateStructureCache();
}
