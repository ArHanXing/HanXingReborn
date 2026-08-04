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
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import reborncore.api.blockentity.IMachineGuiHandler;
import reborncore.common.blocks.BlockMachineBase;
import techreborn.init.TRBlockSettings;

import java.util.List;
import java.util.function.BiFunction;

/**
 * @author drcrazy
 */
public class GenericMachineBlock extends BlockMachineBase {

	private final IMachineGuiHandler gui;
	final BiFunction<BlockPos, BlockState, BlockEntity> blockEntityClass;

	/**
	 * Maximum parallel count shown in the tooltip. 0 hides the line.
	 */
	private final int maxParallel;

	public GenericMachineBlock(IMachineGuiHandler gui, BiFunction<BlockPos, BlockState, BlockEntity> blockEntityClass) {
		this(gui, blockEntityClass, 0);
	}

	public GenericMachineBlock(IMachineGuiHandler gui, BiFunction<BlockPos, BlockState, BlockEntity> blockEntityClass,
			int maxParallel) {
		super(TRBlockSettings.genericMachine());
		this.blockEntityClass = blockEntityClass;
		this.gui = gui;
		this.maxParallel = maxParallel;
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		if (blockEntityClass == null) {
			return null;
		}
		return blockEntityClass.apply(pos, state);
	}

	@Override
	public IMachineGuiHandler getGui() {
		return gui;
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
		super.appendTooltip(stack, context, tooltip, type);
		appendMachineTooltip(tooltip);
	}

	/**
	 * Hook for subclasses to add machine-specific tooltip lines.
	 * <p>
	 * The default implementation adds the "maximum parallel" line when the
	 * block was created with a non-zero {@code maxParallel}. Subclasses should
	 * call {@code super.appendMachineTooltip(tooltip)} first, then append their
	 * own lines (e.g. via {@link MultiblockTooltipBuilder}).
	 *
	 * @param tooltip {@link List} the tooltip to append to
	 */
	protected void appendMachineTooltip(List<Text> tooltip) {
		if (maxParallel > 0) {
			tooltip.add(Text.translatable("item.techreborn.multiblock_parallel.tooltip",
					Text.literal(String.valueOf(maxParallel)).formatted(Formatting.YELLOW)));
		}
	}
}
