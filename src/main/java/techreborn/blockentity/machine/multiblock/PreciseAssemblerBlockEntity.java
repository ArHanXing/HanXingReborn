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
 * Precise Assembler: a multiblock with 16 inputs and 4 outputs, running its
 * own {@code precise_assembler} recipe type with up to 16 parallels.
 */
public class PreciseAssemblerBlockEntity extends JsonMultiblockMachineBlockEntity implements BuiltScreenHandlerProvider {

	public PreciseAssemblerBlockEntity(BlockPos pos, BlockState state) {
		super(TRBlockEntities.PRECISE_ASSEMBLER, pos, state, "PreciseAssembler",
				TechRebornConfig.preciseAssemblerMaxInput,
				TechRebornConfig.preciseAssemblerMaxEnergy,
				TRContent.Machine.PRECISE_ASSEMBLER.block, 20);
		final int[] inputs = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
		final int[] outputs = new int[]{16, 17, 18, 19};
		// 16 inputs + 4 outputs + 1 energy slot (slot 20)
		this.inventory = new RebornInventory<>(21, "PreciseAssemblerBlockEntity", 64, this);
		this.crafter = new RecipeCrafter(ModRecipes.PRECISE_ASSEMBLER, this, 16, 4, this.inventory, inputs, outputs);
		this.crafter.setMaxParallel(16);
	}

	@Override
	public String getMultiblockId() {
		return "precise_assembler";
	}

	@Override
	public BuiltScreenHandler createScreenHandler(int syncID, final PlayerEntity player) {
		var builder = new ScreenHandlerBuilder("preciseassembler").player(player.getInventory())
				.inventory().hotbar().addInventory().blockEntity(this);
		// 16 inputs: 4 columns x 4 rows
		int[] xs = new int[]{35, 53, 71, 89};
		int[] ys = new int[]{17, 35, 53, 71};
		int slot = 0;
		for (int y : ys) {
			for (int x : xs) {
				builder.slot(slot++, x, y);
			}
		}
		// 4 outputs: 2 columns x 2 rows
		builder.outputSlot(16, 125, 17).outputSlot(17, 143, 17).outputSlot(18, 125, 35).outputSlot(19, 143, 35)
				.energySlot(20, 8, 72)
				.syncEnergyValue().syncCrafterValue();
		return builder.addInventory().create(this, syncID);
	}
}
