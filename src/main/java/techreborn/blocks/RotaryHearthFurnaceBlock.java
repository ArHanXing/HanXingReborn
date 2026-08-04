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
 * The Rotary Hearth Furnace block. Runs every Industrial Blast Furnace recipe
 * at 0.5x time and 0.5x power, which is reflected in the item tooltip.
 */
public class RotaryHearthFurnaceBlock extends GenericMachineBlock {

	public RotaryHearthFurnaceBlock(IMachineGuiHandler gui, BiFunction<BlockPos, BlockState, BlockEntity> blockEntityClass) {
		super(gui, blockEntityClass);
	}

	@Override
	protected void appendMachineTooltip(List<Text> tooltip) {
		MultiblockTooltipBuilder.create()
				.recipeTypes(ModRecipes.ROTARY_HEARTH_FURNACE, List.of(ModRecipes.BLAST_FURNACE))
				.multipliers(List.of(ModRecipes.BLAST_FURNACE), 0.5F, 0.5F)
				.note("item.techreborn.rotary_hearth_furnace.parallel")
				.appendTo(tooltip);
	}
}
