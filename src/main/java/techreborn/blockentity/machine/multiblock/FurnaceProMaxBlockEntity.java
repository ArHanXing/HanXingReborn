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

package techreborn.blockentity.machine.multiblock;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import reborncore.common.screen.BuiltScreenHandler;
import reborncore.common.screen.BuiltScreenHandlerProvider;
import reborncore.common.screen.builder.ScreenHandlerBuilder;
import reborncore.common.util.RebornInventory;
import techreborn.config.TechRebornConfig;
import techreborn.init.TRBlockEntities;
import techreborn.init.TRContent;
import techreborn.recipe.FurnaceProMaxCrafter;

/**
 * Furnace Pro Max ("异步！并行？超泥土！熔炉"): a joke / performance-test
 * multiblock that runs every vanilla furnace recipe in 1 tick at 1 EU each,
 * with the maximum possible parallelism (Integer.MAX_VALUE). Mostly used to
 * stress-test the JSON multiblock validation and parallel crafting.
 */
public class FurnaceProMaxBlockEntity extends JsonMultiblockMachineBlockEntity implements BuiltScreenHandlerProvider {

	public FurnaceProMaxBlockEntity(BlockPos pos, BlockState state) {
		super(TRBlockEntities.FURNACE_PRO_MAX, pos, state, "FurnaceProMax",
				TechRebornConfig.furnaceProMaxMaxInput,
				TechRebornConfig.furnaceProMaxMaxEnergy,
				TRContent.Machine.FURNACE_PRO_MAX.block, 3);
		final int[] inputs = new int[]{0};
		final int[] outputs = new int[]{1};
		// Slot 3 is the energy/battery slot (energySlot), matching other machines.
		this.inventory = new RebornInventory<>(4, "FurnaceProMaxBlockEntity", 64, this);
		this.crafter = new FurnaceProMaxCrafter(this, 1, 1, this.inventory, inputs, outputs);
		this.crafter.setMaxParallel(Integer.MAX_VALUE);
	}

	@Override
	public String getMultiblockId() {
		return "furnace_pro_max";
	}

	@Override
	public BuiltScreenHandler createScreenHandler(int syncID, final PlayerEntity player) {
		return new ScreenHandlerBuilder("furnacepromax").player(player.getInventory()).inventory().hotbar().addInventory()
				.blockEntity(this).slot(0, 55, 45).outputSlot(1, 101, 45).energySlot(3, 8, 72)
				.syncEnergyValue().syncCrafterValue().addInventory().create(this, syncID);
	}
}
