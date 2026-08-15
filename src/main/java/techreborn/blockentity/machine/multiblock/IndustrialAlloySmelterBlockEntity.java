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
import techreborn.init.ModRecipes;
import techreborn.init.TRBlockEntities;
import techreborn.init.TRContent;
import techreborn.recipe.ProxyRecipeCrafter;

import java.util.List;

/**
 * Industrial Alloy Smelter: runs every alloy smelter recipe at 0.5x time and
 * 0.8x power, up to 16 parallels.
 */
public class IndustrialAlloySmelterBlockEntity extends JsonMultiblockMachineBlockEntity implements BuiltScreenHandlerProvider {

	public IndustrialAlloySmelterBlockEntity(BlockPos pos, BlockState state) {
		super(TRBlockEntities.INDUSTRIAL_ALLOY_SMELTER, pos, state, "IndustrialAlloySmelter",
				TechRebornConfig.industrialAlloySmelterMaxInput,
				TechRebornConfig.industrialAlloySmelterMaxEnergy,
				TRContent.Machine.INDUSTRIAL_ALLOY_SMELTER.block, 3);
		final int[] inputs = new int[]{0, 1};
		final int[] outputs = new int[]{2};
		this.inventory = new RebornInventory<>(4, "IndustrialAlloySmelterBlockEntity", 64, this);
		this.crafter = new ProxyRecipeCrafter(
				ModRecipes.INDUSTRIAL_ALLOY_SMELTER,
				List.of(ModRecipes.ALLOY_SMELTER),
				0.5F, 0.8F,
				this, 2, 1, this.inventory, inputs, outputs);
		this.crafter.setMaxParallel(16);
	}

	@Override
	public String getMultiblockId() {
		return "industrial_alloy_smelter";
	}

	@Override
	public BuiltScreenHandler createScreenHandler(int syncID, final PlayerEntity player) {
		return new ScreenHandlerBuilder("industrialalloysmelter").player(player.getInventory()).inventory().hotbar().addInventory()
				.blockEntity(this).slot(0, 55, 26).slot(1, 55, 45).outputSlot(2, 101, 45).energySlot(3, 8, 72)
				.syncEnergyValue().syncCrafterValue().addInventory().create(this, syncID);
	}
}
