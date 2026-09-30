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

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.recipe.RecipeType;
import net.minecraft.util.math.BlockPos;

import reborncore.common.crafting.RebornRecipe;
import reborncore.common.recipes.RecipeCrafter;
import reborncore.common.screen.BuiltScreenHandler;
import reborncore.common.screen.BuiltScreenHandlerProvider;
import reborncore.common.screen.builder.ScreenHandlerBuilder;
import reborncore.common.util.RebornInventory;

/**
 * Common base of the independent multiblock machines that share the
 * distillation tower's 4-input / 6-output shape.
 * <p>
 * Slot layout: inputs 0-3 (2x2 on the left), outputs 4-9 (3x2 on the right),
 * energy 10. {@code GuiFourInSixOut} draws the matching frames, so the two
 * coordinates sets have to change together.
 */
public abstract class FourInSixOutMachineBlockEntity extends JsonMultiblockMachineBlockEntity implements BuiltScreenHandlerProvider {

	protected static final int INPUT_SLOTS = 4;
	protected static final int OUTPUT_SLOTS = 6;
	private static final int ENERGY_SLOT = 10;

	public FourInSixOutMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, String name,
			int maxInput, int maxEnergy, Block toolDrop) {
		super(type, pos, state, name, maxInput, maxEnergy, toolDrop, ENERGY_SLOT);
	}

	/**
	 * Wires the inventory and crafter. Called from the subclass constructor
	 * because the recipe type is not available before it.
	 *
	 * @param recipeType  {@link RecipeType} the machine's own recipe type
	 * @param parallel    {@code int} maximum number of parallel runs
	 * @param invName     {@link String} inventory name used in error messages
	 */
	protected void initCrafter(RecipeType<? extends RebornRecipe> recipeType, int parallel, String invName) {
		final int[] inputs = new int[]{0, 1, 2, 3};
		final int[] outputs = new int[]{4, 5, 6, 7, 8, 9};
		this.inventory = new RebornInventory<>(INPUT_SLOTS + OUTPUT_SLOTS + 1, invName, 64, this);
		this.crafter = new RecipeCrafter(recipeType, this, INPUT_SLOTS, OUTPUT_SLOTS, this.inventory, inputs, outputs);
		this.crafter.setMaxParallel(parallel);
	}

	@Override
	public BuiltScreenHandler createScreenHandler(int syncID, final PlayerEntity player) {
		return new ScreenHandlerBuilder("fourinsixout").player(player.getInventory()).inventory().hotbar().addInventory()
				.blockEntity(this)
				// 4 input slots: 2 columns x 2 rows
				.slot(0, 35, 26).slot(1, 53, 26).slot(2, 35, 44).slot(3, 53, 44)
				// 6 output slots: 3 columns x 2 rows
				.outputSlot(4, 89, 26).outputSlot(5, 107, 26).outputSlot(6, 125, 26)
				.outputSlot(7, 89, 44).outputSlot(8, 107, 44).outputSlot(9, 125, 44)
				.energySlot(ENERGY_SLOT, 8, 72).syncEnergyValue().syncCrafterValue()
				.addInventory().create(this, syncID);
	}

	@Override
	public boolean canCraft(RebornRecipe rebornRecipe) {
		return isMultiblockValid();
	}
}
