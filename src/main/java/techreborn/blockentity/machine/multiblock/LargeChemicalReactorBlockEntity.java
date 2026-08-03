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
import reborncore.common.crafting.RebornRecipe;
import reborncore.common.recipes.RecipeCrafter;
import reborncore.common.screen.BuiltScreenHandler;
import reborncore.common.screen.BuiltScreenHandlerProvider;
import reborncore.common.screen.builder.ScreenHandlerBuilder;
import reborncore.common.util.RebornInventory;
import techreborn.config.TechRebornConfig;
import techreborn.init.ModRecipes;
import techreborn.init.TRBlockEntities;
import techreborn.init.TRContent;

public class LargeChemicalReactorBlockEntity extends JsonMultiblockMachineBlockEntity implements BuiltScreenHandlerProvider {

	public LargeChemicalReactorBlockEntity(BlockPos pos, BlockState state) {
		super(TRBlockEntities.LARGE_CHEMICAL_REACTOR, pos, state, "LargeChemicalReactor",
				TechRebornConfig.largeChemicalReactorMaxInput,
				TechRebornConfig.largeChemicalReactorMaxEnergy,
				TRContent.Machine.LARGE_CHEMICAL_REACTOR.block, 10);
		final int[] inputs = new int[]{0, 1, 2, 3, 4, 5};
		final int[] outputs = new int[]{6, 7, 8, 9};
		this.inventory = new RebornInventory<>(11, "LargeChemicalReactorBlockEntity", 64, this);
		this.crafter = new RecipeCrafter(ModRecipes.LARGE_CHEMICAL_REACTOR, this, 6, 4, this.inventory, inputs, outputs);
	}

	@Override
	public String getMultiblockId() {
		return "large_chemical_reactor";
	}

	@Override
	public BuiltScreenHandler createScreenHandler(int syncID, final PlayerEntity player) {
		return new ScreenHandlerBuilder("largechemicalreactor")
				.player(player.getInventory()).inventory().hotbar().addInventory()
				.blockEntity(this)
				// 6 input slots: 3 columns x 2 rows
				.slot(0, 35, 26).slot(1, 53, 26).slot(2, 71, 26)
				.slot(3, 35, 44).slot(4, 53, 44).slot(5, 71, 44)
				// 4 output slots: 2 columns x 2 rows
				.outputSlot(6, 107, 26).outputSlot(7, 125, 26)
				.outputSlot(8, 107, 44).outputSlot(9, 125, 44)
				// Energy slot
				.energySlot(10, 8, 72)
				.syncEnergyValue().syncCrafterValue()
				.addInventory().create(this, syncID);
	}

	@Override
	public boolean canCraft(RebornRecipe rebornRecipe) {
		return isMultiblockValid();
	}
}
