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

package techreborn.blockentity.machine.multiblock.casing;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import techreborn.blocks.misc.BlockMachineCasing;
import techreborn.init.TRBlockEntities;
import techreborn.utils.DirectionUtils;

/**
 * Block entity of the machine casings, the coils and the structure blocks that
 * share them.
 * <p>
 * It has exactly one job: on first load it repairs the neighbouring-casing
 * blockstate so the casing textures connect again (a fix for worlds saved by
 * older versions). The multiblock structures themselves are JSON definitions
 * verified by {@code JsonMultiblockMachineBlockEntity}, so nothing tracks
 * "assembled" casings any more — which is why this is a plain {@link BlockEntity}
 * instead of a part of the removed RebornCore controller system.
 */
public class MachineCasingBlockEntity extends BlockEntity implements BlockEntityTicker<MachineCasingBlockEntity> {

	private boolean firstLoad = true;

	public MachineCasingBlockEntity(BlockPos pos, BlockState state) {
		super(TRBlockEntities.MACHINE_CASINGS, pos, state);
	}

	@Override
	public void tick(World world, BlockPos pos, BlockState state, MachineCasingBlockEntity blockEntity) {
		// Compatible with old versions — only BlockMachineCasing has the neighbors property
		if (firstLoad) {
			firstLoad = false;
			if (state.getBlock() instanceof BlockMachineCasing && state.get(DirectionUtils.HORIZONTAL_NEIGHBORS) == 0) {
				DirectionUtils.loadHorizontalNeighbors(world, pos, state, block -> block instanceof BlockMachineCasing);
			}
		}
	}
}
