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

package techreborn.blocks.storage.fluid;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import org.jetbrains.annotations.Nullable;
import reborncore.api.blockentity.IMachineGuiHandler;
import reborncore.common.blocks.BlockMachineBase;
import techreborn.blockentity.GuiType;
import techreborn.blockentity.storage.fluid.WaterSinkBlockEntity;
import techreborn.init.TRBlockSettings;

/**
 * The water sink: a decorative basin that holds water and refills itself.
 * <p>
 * The borrowed model is a sink standing against a wall and only carries a
 * horizontal facing, so this block registers with {@code hasCustomStates = true}
 * to suppress the {@code ACTIVE} property {@link BlockMachineBase} would
 * otherwise inject - a blockstate the model does not define would render as a
 * missing model.
 * <p>
 * Appearance and model come from the Just Sink project; the block entity, tank
 * and GUI are TechReborn's own tank code.
 */
public class WaterSinkBlock extends BlockMachineBase {

	public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;

	/** The sink is not a full cube; its outline follows the basin and the tap. */
	private static final VoxelShape SHAPE = VoxelShapes.union(
		Block.createCuboidShape(0, 0, 0, 16, 14, 16),
		Block.createCuboidShape(2, 14, 2, 14, 16, 14)
	);

	public WaterSinkBlock() {
		super(TRBlockSettings.tankUnit(), true);
		setDefaultState(this.getStateManager().getDefaultState().with(FACING, net.minecraft.util.math.Direction.NORTH));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPE;
	}

	@Nullable
	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
	}

	@Override
	public BlockState rotate(BlockState state, net.minecraft.util.BlockRotation rotation) {
		return state.with(FACING, rotation.rotate(state.get(FACING)));
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new WaterSinkBlockEntity(pos, state);
	}

	@Override
	public IMachineGuiHandler getGui() {
		return GuiType.WATER_SINK;
	}
}
