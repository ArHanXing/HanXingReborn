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
 * Casing of the Digital Miner. Uses the tier 1 machine textures
 * ({@code machine_side} / {@code machine_top} / {@code machine_bottom}) so the
 * 3x2 body below the controller matches the controller's own shell.
 * <p>
 * Mirrors the {@link BlockSpaceElevatorPowerModule} bloom idea: while the miner
 * is running its body switches to the {@code active} state and emits light, so
 * the machine reads as "on" at a glance from any side.
 */
public class BlockTier1MachineCasing extends BlockMultiblockBase implements BlockEntityProvider {

	public static final BooleanProperty ACTIVE = BooleanProperty.of("active");

	public BlockTier1MachineCasing() {
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
