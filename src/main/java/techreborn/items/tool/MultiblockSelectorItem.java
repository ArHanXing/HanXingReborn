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

package techreborn.items.tool;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;

import java.util.List;

/**
 * A debugging/authoring tool used to record multiblock structures.
 * <p>
 * Rendered as a stick with an enchantment glint (see the item model and the
 * {@code enchantment_glint_override} data component). All interaction logic is
 * client-side and lives in
 * {@code techreborn.client.multiblock.MultiblockSelector}.
 */
public class MultiblockSelectorItem extends Item {

	public MultiblockSelectorItem() {
		super(new Item.Settings().component(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true));
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
		super.appendTooltip(stack, context, tooltip, type);
		tooltip.add(Text.translatable("item.techreborn.multiblock_selector.tooltip.0"));
		tooltip.add(Text.translatable("item.techreborn.multiblock_selector.tooltip.1"));
		tooltip.add(Text.translatable("item.techreborn.multiblock_selector.tooltip.2"));
		tooltip.add(Text.translatable("item.techreborn.multiblock_selector.tooltip.3"));
		tooltip.add(Text.translatable("item.techreborn.multiblock_selector.tooltip.4"));
	}
}
