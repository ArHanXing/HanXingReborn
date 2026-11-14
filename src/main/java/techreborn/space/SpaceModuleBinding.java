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

package techreborn.space;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import techreborn.blockentity.machine.multiblock.SpaceElevatorBlockEntity;

/** Loaded-module presence is independent of power, recipes and structure validity. */
public final class SpaceModuleBinding {
	private SpaceModuleBinding() { }

	public static boolean inRange(BlockPos host, BlockPos module) {
		long dx = (long) host.getX() - module.getX();
		long dz = (long) host.getZ() - module.getZ();
		return Math.abs(dx) <= 128 && Math.abs(dz) <= 128 && dx * dx + dz * dz <= 128L * 128;
	}

	public static void heartbeat(BlockEntity module, BlockPos hostPos) {
		World world = module.getWorld();
		if (world == null || world.isClient || world.getRegistryKey() != World.OVERWORLD || hostPos == null
				|| !inRange(hostPos, module.getPos()) || !world.isChunkLoaded(hostPos)) { return; }
		if (world.getBlockEntity(hostPos) instanceof SpaceElevatorBlockEntity host) { host.registerModule(module.getPos()); }
	}
}
