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

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import techreborn.blocks.misc.BlockCoil;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reference-counted control of the {@link BlockCoil#ACTIVE} blockstate.
 * <p>
 * A coil is lit while <em>at least one</em> machine that has it in its structure
 * is running. Machines acquire the coils when they start working and release
 * them when they stop; a coil only goes dark once the last holder lets go.
 * <p>
 * The count matters because structures may deliberately be shared: two
 * controllers can be built around the same coil bank. Without it, the machine
 * that happens to stop first would switch the coils off under the one that is
 * still running, which is both wrong visually and a lie about the structure.
 * This is the same reference-counting idea Flight API uses for shared state.
 * <p>
 * Server side only. Coil positions come from
 * {@link CoilHeatScanner#collectPositions}, i.e. the same JSON pattern the
 * structure is validated against, so a machine only ever lights coils that are
 * actually part of its own structure.
 */
public final class CoilBloomController {

	/**
	 * Coil position to the machines currently holding it lit. Keyed by world
	 * identity so equal positions in different dimensions stay apart.
	 */
	private static final Map<World, Map<BlockPos, Set<Object>>> OWNERS = new IdentityHashMap<>();

	/** The positions each machine currently holds, so it can release them again. */
	private static final Map<Object, Set<BlockPos>> HELD = new IdentityHashMap<>();

	static {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> clear());
	}

	private CoilBloomController() {
	}

	/**
	 * Drops every registration. Only needed when the server stops; coils live in
	 * loaded chunks, so there is nothing to switch off at that point.
	 */
	public static void clear() {
		OWNERS.clear();
		HELD.clear();
	}

	/**
	 * Brings the coils of a machine in line with its running state.
	 * <p>
	 * Call this on every state change, and once from {@code onLoad} so a coil
	 * whose blockstate was left lit by a crash or by a replaced block is put
	 * back in sync. Coils still held by another machine are left alone.
	 *
	 * @param world    {@link World} the world the machine is in
	 * @param owner    {@link Object} the machine, compared by identity
	 * @param positions {@link List} every position of the machine's structure
	 * @param active   {@code boolean} whether the machine is running
	 */
	public static void refresh(World world, Object owner, List<BlockPos> positions, boolean active) {
		release(world, owner);
		if (active) {
			acquire(world, owner, positions);
		} else {
			// A coil may have been left lit by a previous session; clear the
			// ones nobody holds any more.
			for (BlockPos pos : positions) {
				if (isHeld(world, pos)) {
					continue;
				}
				BlockState state = world.getBlockState(pos);
				if (state.getBlock() instanceof BlockCoil && state.get(BlockCoil.ACTIVE)) {
					world.setBlockState(pos, state.with(BlockCoil.ACTIVE, false), Block.NOTIFY_LISTENERS);
				}
			}
		}
	}

	/**
	 * Registers {@code owner} as a holder of every coil among {@code positions},
	 * lighting the ones that were dark.
	 *
	 * @param world    {@link World} the world the machine is in
	 * @param owner    {@link Object} the machine, compared by identity
	 * @param positions {@link List} every position of the machine's structure
	 */
	private static void acquire(World world, Object owner, List<BlockPos> positions) {
		if (world.isClient || !(world instanceof ServerWorld)) {
			return;
		}
		Map<BlockPos, Set<Object>> owners = OWNERS.computeIfAbsent(world, w -> new HashMap<>());
		Set<BlockPos> held = new HashSet<>();

		for (BlockPos pos : positions) {
			BlockState state = world.getBlockState(pos);
			if (!(state.getBlock() instanceof BlockCoil)) {
				continue;
			}
			BlockPos key = pos.toImmutable();
			Set<Object> holders = owners.computeIfAbsent(key, p -> Collections.newSetFromMap(new IdentityHashMap<>()));
			boolean wasUnheld = holders.isEmpty();
			holders.add(owner);
			held.add(key);
			if (wasUnheld && !state.get(BlockCoil.ACTIVE)) {
				world.setBlockState(key, state.with(BlockCoil.ACTIVE, true), Block.NOTIFY_LISTENERS);
			}
		}

		if (!held.isEmpty()) {
			HELD.put(owner, held);
		}
	}

	/**
	 * Removes {@code owner} from every coil it holds, darkening the ones nobody
	 * else holds.
	 *
	 * @param world {@link World} the world the machine is in
	 * @param owner {@link Object} the machine, compared by identity
	 */
	public static void release(World world, Object owner) {
		Set<BlockPos> held = HELD.remove(owner);
		if (held == null) {
			return;
		}
		Map<BlockPos, Set<Object>> owners = OWNERS.get(world);
		if (owners == null) {
			return;
		}
		for (BlockPos pos : held) {
			Set<Object> holders = owners.get(pos);
			if (holders == null) {
				continue;
			}
			holders.remove(owner);
			if (!holders.isEmpty()) {
				continue;
			}
			owners.remove(pos);
			BlockState state = world.getBlockState(pos);
			if (state.getBlock() instanceof BlockCoil && state.get(BlockCoil.ACTIVE)) {
				world.setBlockState(pos, state.with(BlockCoil.ACTIVE, false), Block.NOTIFY_LISTENERS);
			}
		}
		if (owners.isEmpty()) {
			OWNERS.remove(world);
		}
	}

	private static boolean isHeld(World world, BlockPos pos) {
		Map<BlockPos, Set<Object>> owners = OWNERS.get(world);
		if (owners == null) {
			return false;
		}
		Set<Object> holders = owners.get(pos);
		return holders != null && !holders.isEmpty();
	}
}
