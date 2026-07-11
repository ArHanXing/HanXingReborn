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

package techreborn.blocks.misc;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import reborncore.common.multiblock.BlockMultiblockBase;
import techreborn.blockentity.machine.multiblock.casing.MachineCasingBlockEntity;
import techreborn.init.TRBlockSettings;

import java.util.List;

/**
 * Coil block for GT5-style multiblock machines like the Industrial Blast Furnace.
 * <p>
 * Extends {@link BlockMultiblockBase} directly (not {@link BlockMachineCasing})
 * to avoid the horizontal-neighbor connecting-texture system — coils use simple
 * cube_all models.
 * <p>
 * The {@link #heat} value is the fixed temperature this coil tier provides; in
 * the IBF, only one coil type may be used and its heat is the machine's heat.
 */
public class BlockCoil extends BlockMultiblockBase implements BlockEntityProvider {

	public static final BooleanProperty ACTIVE = BooleanProperty.of("active");

	public final int heat;

	public BlockCoil(int heat) {
		super(TRBlockSettings.machineCasing());
		this.heat = heat;
		setDefaultState(getDefaultState().with(ACTIVE, false));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		super.appendProperties(builder);
		builder.add(ACTIVE);
	}

	public static int getHeatFromState(BlockState state) {
		if (state.getBlock() instanceof BlockCoil coil) {
			return coil.heat;
		}
		return 0;
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new MachineCasingBlockEntity(pos, state);
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType options) {
		super.appendTooltip(stack, context, tooltip, options);
		tooltip.add(Text.translatable("techreborn.tooltip.coil_heat", heat).formatted(Formatting.GOLD));
	}
}
