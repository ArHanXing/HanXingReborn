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
import reborncore.common.recipes.RecipeCrafter;
import reborncore.common.screen.BuiltScreenHandler;
import reborncore.common.screen.BuiltScreenHandlerProvider;
import reborncore.common.screen.builder.ScreenHandlerBuilder;
import reborncore.common.util.RebornInventory;
import techreborn.config.TechRebornConfig;
import techreborn.init.ModRecipes;
import techreborn.init.TRBlockEntities;
import techreborn.init.TRContent;

/**
 * IrradiatedMutagensisChamber
 * <p>
 * Independent-recipe multiblock with its own slot layout, up to 16 parallels.
 */
public class IrradiatedMutagensisChamberBlockEntity extends JsonMultiblockMachineBlockEntity implements BuiltScreenHandlerProvider {

	public IrradiatedMutagensisChamberBlockEntity(BlockPos pos, BlockState state) {
		super(TRBlockEntities.IRRADIATED_MUTAGENSIS_CHAMBER, pos, state, "IrradiatedMutagensisChamber",
				TechRebornConfig.irradiatedMutagensisChamberMaxInput, TechRebornConfig.irradiatedMutagensisChamberMaxEnergy,
				TRContent.Machine.IRRADIATED_MUTAGENSIS_CHAMBER.block, 6);
		final int[] inputs = new int[]{0, 1, 2, 3};
		final int[] outputs = new int[]{4, 5};
		this.inventory = new RebornInventory<>(7, "IrradiatedMutagensisChamberBlockEntity", 64, this);
		this.crafter = new RecipeCrafter(ModRecipes.IRRADIATED_MUTAGENSIS_CHAMBER, this, 4, 2, this.inventory, inputs, outputs);
		this.crafter.setMaxParallel(16);
	}

	@Override
	public String getMultiblockId() {
		return "irradiated_mutagensis_chamber";
	}

	@Override
	public boolean canCraft(RebornRecipe rebornRecipe) {
		return isMultiblockValid();
	}

	@Override
	public BuiltScreenHandler createScreenHandler(int syncID, final PlayerEntity player) {
		return new ScreenHandlerBuilder("irradiatedmutagensischamber").player(player.getInventory()).inventory().hotbar().addInventory()
				.blockEntity(this)
				.slot(0, 35, 26).slot(1, 53, 26).slot(2, 35, 44).slot(3, 53, 44)
				.outputSlot(4, 107, 35).outputSlot(5, 125, 35)
				.energySlot(6, 8, 72).syncEnergyValue().syncCrafterValue()
				.addInventory().create(this, syncID);
	}
}
