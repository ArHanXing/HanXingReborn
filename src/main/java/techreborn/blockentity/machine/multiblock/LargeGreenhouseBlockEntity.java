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

package techreborn.blockentity.machine.multiblock;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import reborncore.common.crafting.RebornRecipe;
import reborncore.common.screen.BuiltScreenHandler;
import reborncore.common.screen.BuiltScreenHandlerProvider;
import reborncore.common.screen.builder.ScreenHandlerBuilder;
import reborncore.common.util.RebornInventory;
import techreborn.config.TechRebornConfig;
import techreborn.init.ModRecipes;
import techreborn.init.TRBlockEntities;
import techreborn.init.TRContent;
import techreborn.recipe.OreCrusherRecipeCrafter;

import java.util.List;

/**
 * Large Greenhouse: duplicates crops (wheat, potatoes, carrots, ...) up to 16
 * parallels. Like the ore crusher, the input seed/crop is never consumed: it
 * acts as a permanent catalyst while the outputs are produced into the two
 * output slots. Has no time or power reduction.
 */
public class LargeGreenhouseBlockEntity extends JsonMultiblockMachineBlockEntity implements BuiltScreenHandlerProvider {

	public LargeGreenhouseBlockEntity(BlockPos pos, BlockState state) {
		super(TRBlockEntities.LARGE_GREENHOUSE, pos, state, "LargeGreenhouse",
				TechRebornConfig.largeGreenhouseMaxInput,
				TechRebornConfig.largeGreenhouseMaxEnergy,
				TRContent.Machine.LARGE_GREENHOUSE.block, 3);
		final int[] inputs = new int[]{0};
		final int[] outputs = new int[]{1, 2};
		this.inventory = new RebornInventory<>(4, "LargeGreenhouseBlockEntity", 64, this);
		// Runs its own greenhouse recipes (no proxies, no time/power reduction)
		this.crafter = new OreCrusherRecipeCrafter(
				ModRecipes.GREENHOUSE, List.of(), 1F, 1F,
				this, 1, 2, this.inventory, inputs, outputs);
		this.crafter.setMaxParallel(16);
	}

	@Override
	public String getMultiblockId() {
		return "large_greenhouse";
	}

	@Override
	public BuiltScreenHandler createScreenHandler(int syncID, final PlayerEntity player) {
		return new ScreenHandlerBuilder("largegreenhouse")
				.player(player.getInventory()).inventory().hotbar().addInventory()
				.blockEntity(this)
				.slot(0, 55, 45).outputSlot(1, 101, 45).outputSlot(2, 121, 45).energySlot(3, 8, 72)
				.syncEnergyValue().syncCrafterValue()
				.addInventory().create(this, syncID);
	}

	@Override
	public boolean canCraft(RebornRecipe rebornRecipe) {
		return isMultiblockValid();
	}
}
