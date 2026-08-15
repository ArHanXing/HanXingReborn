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

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Tracks every loaded {@link IMultiblockStructureMember} so that block
 * changes (e.g. a player breaking part of the structure) can invalidate the
 * cached multiblock validity instantly.
 * <p>
 * Machines register themselves in {@code onLoad()} and unregister in
 * {@code markRemoved()}. The set uses weak references so a forgotten machine
 * can never leak.
 */
public final class MultiblockStructureTracker {

	private static final Set<IMultiblockStructureMember> MACHINES =
			Collections.newSetFromMap(new WeakHashMap<>());

	private MultiblockStructureTracker() {
	}

	public static void register(IMultiblockStructureMember machine) {
		MACHINES.add(machine);
	}

	public static void unregister(IMultiblockStructureMember machine) {
		MACHINES.remove(machine);
	}

	/**
	 * Called whenever a block changes in the world (currently player block
	 * breaks). Any machine whose cached structure bounding box contains the
	 * position has its multiblock validity cache invalidated so the next
	 * check re-verifies the structure.
	 *
	 * @param world {@link World} the world the block changed in
	 * @param pos   {@link BlockPos} the changed block position
	 */
	public static void onBlockChanged(World world, BlockPos pos) {
		for (IMultiblockStructureMember machine : MACHINES) {
			if (machine.getWorld() == world && machine.isPositionInStructure(pos)) {
				machine.invalidateStructureCache();
			}
		}
	}
}
