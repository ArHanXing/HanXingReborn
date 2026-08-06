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
 * Primitive Distillation Tower: runs every distillation tower recipe at 1.5x
 * time and 0.8x power, no parallelism (the "old school" tower).
 */
public class PrimitiveDistillationTowerBlockEntity extends JsonMultiblockMachineBlockEntity implements BuiltScreenHandlerProvider {

	public PrimitiveDistillationTowerBlockEntity(BlockPos pos, BlockState state) {
		super(TRBlockEntities.PRIMITIVE_DISTILLATION_TOWER, pos, state, "PrimitiveDistillationTower",
				TechRebornConfig.primitiveDistillationTowerMaxInput,
				TechRebornConfig.primitiveDistillationTowerMaxEnergy,
				TRContent.Machine.PRIMITIVE_DISTILLATION_TOWER.block, 10);
		final int[] inputs = new int[]{0, 1, 2, 3};
		final int[] outputs = new int[]{4, 5, 6, 7, 8, 9};
		this.inventory = new RebornInventory<>(11, "PrimitiveDistillationTowerBlockEntity", 64, this);
		this.crafter = new ProxyRecipeCrafter(
				ModRecipes.PRIMITIVE_DISTILLATION_TOWER,
				List.of(ModRecipes.DISTILLATION_TOWER),
				1.5F, 0.8F,
				this, 4, 6, this.inventory, inputs, outputs);
		this.crafter.setMaxParallel(1);
	}

	@Override
	public String getMultiblockId() {
		return "primitive_distillation_tower";
	}

	@Override
	public BuiltScreenHandler createScreenHandler(int syncID, final PlayerEntity player) {
		return new ScreenHandlerBuilder("Primitivedistillationtower").player(player.getInventory()).inventory().hotbar().addInventory()
				.blockEntity(this)
				// 4 input slots: 2 columns x 2 rows
				.slot(0, 35, 26).slot(1, 53, 26).slot(2, 35, 44).slot(3, 53, 44)
				// 6 output slots: 3 columns x 2 rows
				.outputSlot(4, 89, 26).outputSlot(5, 107, 26).outputSlot(6, 125, 26)
				.outputSlot(7, 89, 44).outputSlot(8, 107, 44).outputSlot(9, 125, 44)
				.energySlot(10, 8, 72).syncEnergyValue().syncCrafterValue()
				.addInventory().create(this, syncID);
	}

	@Override
	public boolean canCraft(RebornRecipe rebornRecipe) {
		return isMultiblockValid();
	}
}
