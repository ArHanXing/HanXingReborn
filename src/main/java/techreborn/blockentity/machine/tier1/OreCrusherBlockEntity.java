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

package techreborn.blockentity.machine.tier1;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import reborncore.common.screen.BuiltScreenHandler;
import reborncore.common.screen.BuiltScreenHandlerProvider;
import reborncore.common.screen.builder.ScreenHandlerBuilder;
import reborncore.common.util.RebornInventory;
import techreborn.blockentity.machine.GenericMachineBlockEntity;
import techreborn.config.TechRebornConfig;
import techreborn.init.ModRecipes;
import techreborn.init.TRBlockEntities;
import techreborn.init.TRContent;
import techreborn.recipe.OreCrusherRecipeCrafter;

import java.util.List;

/**
 * Ore Crusher: turns stone-like inputs (cobblestone, obsidian, granite,
 * diorite, andesite, end stone) into 16 stone per run. The input item is
 * NOT consumed, it acts as a permanent catalyst.
 */
public class OreCrusherBlockEntity extends GenericMachineBlockEntity implements BuiltScreenHandlerProvider {

	public OreCrusherBlockEntity(BlockPos pos, BlockState state) {
		super(TRBlockEntities.ORE_CRUSHER, pos, state, "OreCrusher",
				TechRebornConfig.oreCrusherMaxInput,
				TechRebornConfig.oreCrusherMaxEnergy,
				TRContent.Machine.ORE_CRUSHER.block, 2);
		final int[] inputs = new int[]{0};
		final int[] outputs = new int[]{1};
		this.inventory = new RebornInventory<>(3, "OreCrusherBlockEntity", 64, this);
		// No proxy types: the small machine only runs its own recipes.
		this.crafter = new OreCrusherRecipeCrafter(
				ModRecipes.ORE_CRUSHER, List.of(), 1F, 1F,
				this, 1, 1, this.inventory, inputs, outputs);
	}

	@Override
	public BuiltScreenHandler createScreenHandler(int syncID, final PlayerEntity player) {
		return new ScreenHandlerBuilder("orecrusher").player(player.getInventory()).inventory().hotbar().addInventory().blockEntity(this)
			.slot(0, 55, 45).outputSlot(1, 101, 45).energySlot(2, 8, 72).syncEnergyValue().syncCrafterValue()
			.addInventory().create(this, syncID);
	}
}
