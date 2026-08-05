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

package techreborn.blocks;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import reborncore.api.blockentity.IMachineGuiHandler;
import techreborn.init.ModRecipes;

import java.util.List;
import java.util.function.BiFunction;

/**
 * The Large Wire Mill block. Runs every regular wire mill recipe at
 * 0.5x time and 0.8x power, up to 16 parallels.
 */
public class LargeWireMillBlock extends GenericMachineBlock {

	public LargeWireMillBlock(IMachineGuiHandler gui, BiFunction<BlockPos, BlockState, BlockEntity> blockEntityClass) {
		super(gui, blockEntityClass);
	}

	@Override
	protected void appendMachineTooltip(List<Text> tooltip) {
		MultiblockTooltipBuilder.create()
				.recipeTypes(ModRecipes.LARGE_WIRE_MILL, List.of(ModRecipes.WIRE_MILL))
				.multipliers(List.of(ModRecipes.WIRE_MILL), 0.8F, 0.5F)
				.maxParallel(16)
				.appendTo(tooltip);
	}
}
