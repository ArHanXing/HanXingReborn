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

package techreborn.blocks.misc;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.math.BlockPos;
import reborncore.common.multiblock.BlockMultiblockBase;
import techreborn.blockentity.machine.multiblock.casing.MachineCasingBlockEntity;
import techreborn.init.TRBlockSettings;

/**
 * Power module block of the Space Elevator multiblock. While the elevator host
 * is running (powered and in the Overworld) every power module inside its
 * structure switches to the {@code active} state: the blockstate picks the
 * "bloom" texture variant and the block emits level-15 light.
 * <p>
 * Mirrors the coil bloom pattern of {@link BlockCoil} (self-adapting: the host
 * scans its JSON structure for these blocks, no fixed positions needed).
 */
public class BlockSpaceElevatorPowerModule extends BlockMultiblockBase implements BlockEntityProvider {

	public static final BooleanProperty ACTIVE = BooleanProperty.of("active");

	public BlockSpaceElevatorPowerModule() {
		super(TRBlockSettings.machineCasing()
			.luminance(state -> state.get(ACTIVE) ? 15 : 0));
		setDefaultState(getDefaultState().with(ACTIVE, false));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		super.appendProperties(builder);
		builder.add(ACTIVE);
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new MachineCasingBlockEntity(pos, state);
	}
}
