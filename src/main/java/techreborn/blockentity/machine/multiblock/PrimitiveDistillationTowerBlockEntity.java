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
import net.minecraft.fluid.Fluid;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import reborncore.common.blockentity.MachineBaseBlockEntity;
import reborncore.common.crafting.RebornRecipe;
import reborncore.common.fluid.container.ItemFluidInfo;
import reborncore.common.screen.BuiltScreenHandler;
import reborncore.common.screen.BuiltScreenHandlerProvider;
import reborncore.common.screen.builder.ScreenHandlerBuilder;
import reborncore.common.util.RebornInventory;
import techreborn.config.TechRebornConfig;
import techreborn.init.ModFluids;
import techreborn.init.ModRecipes;
import techreborn.init.TRBlockEntities;
import techreborn.init.TRContent;
import techreborn.items.DynamicCellItem;
import techreborn.recipe.ProxyRecipeCrafter;

import java.util.List;

/**
 * Primitive Distillation Tower: runs every distillation tower recipe at 1.5x
 * time and 0.8x power, no parallelism (the "old school" tower).
 * <p>
 * <b>Fouling.</b> Every finished run deposits a random 5-10% of impurity. At
 * {@value #IMPURITY_LIMIT}% the tower refuses to start another run and emits a
 * full-strength redstone signal, until it is washed: feeding a
 * {@link ModFluids#CLEANING_SOLVENT} cell into the cleaning slot takes five
 * seconds (hurried along by overclockers) and costs no energy, and knocks
 * {@value #CLEANING_AMOUNT} percentage points off the impurity.
 */
public class PrimitiveDistillationTowerBlockEntity extends JsonMultiblockMachineBlockEntity implements BuiltScreenHandlerProvider {

	/** Slot layout: 4 inputs, 6 outputs, battery, cleaning cell in, empty cell out. */
	private static final int CLEANING_INPUT_SLOT = 11;
	private static final int CLEANING_OUTPUT_SLOT = 12;

	/** Impurity at which the tower locks up. */
	public static final int IMPURITY_LIMIT = 90;
	/** Impurity knocked off by one cleaning cell. */
	public static final int CLEANING_AMOUNT = 50;
	/** Base cleaning duration in ticks, 5 seconds. */
	public static final int CLEANING_TICKS = 100;
	/** Impurity deposited by one finished run, inclusive range. */
	public static final int IMPURITY_MIN_PER_RUN = 5;
	public static final int IMPURITY_MAX_PER_RUN = 10;

	private int impurity;
	private int cleaningTicks;

	public PrimitiveDistillationTowerBlockEntity(BlockPos pos, BlockState state) {
		super(TRBlockEntities.PRIMITIVE_DISTILLATION_TOWER, pos, state, "PrimitiveDistillationTower",
				TechRebornConfig.primitiveDistillationTowerMaxInput,
				TechRebornConfig.primitiveDistillationTowerMaxEnergy,
				TRContent.Machine.PRIMITIVE_DISTILLATION_TOWER.block, 10);
		final int[] inputs = new int[]{0, 1, 2, 3};
		final int[] outputs = new int[]{4, 5, 6, 7, 8, 9};
		this.inventory = new RebornInventory<>(CLEANING_OUTPUT_SLOT + 1, "PrimitiveDistillationTowerBlockEntity", 64, this);
		this.crafter = new FoulingCrafter(this, this.inventory, inputs, outputs);
		this.crafter.setMaxParallel(1);
	}

	@Override
	public String getMultiblockId() {
		return "primitive_distillation_tower";
	}

	// =======================================================================
	// fouling
	// =======================================================================

	/**
	 * @return {@code int} current impurity, 0-100
	 */
	public int getImpurity() {
		return impurity;
	}

	/**
	 * @return {@code boolean} whether the impurity has locked the tower up
	 */
	public boolean isBlocked() {
		return impurity >= IMPURITY_LIMIT;
	}

	/**
	 * Client-side setter for the synced impurity. The value is a plain field, so
	 * the screen handler mirrors it directly instead of keeping a second copy.
	 *
	 * @param impurity {@code int} current impurity, 0-100
	 */
	public void setImpurity(int impurity) {
		this.impurity = impurity;
	}

	private void addImpurity(World world) {
		int gained = IMPURITY_MIN_PER_RUN
				+ world.random.nextInt(IMPURITY_MAX_PER_RUN - IMPURITY_MIN_PER_RUN + 1);
		impurity = Math.min(100, impurity + gained);
		markDirty();
	}

	/**
	 * @param stack {@link ItemStack} the stack to test
	 * @return {@code boolean} whether it is a cleaning solvent cell
	 */
	public static boolean isCleaningCell(ItemStack stack) {
		if (stack.isEmpty() || !(stack.getItem() instanceof ItemFluidInfo info)) {
			return false;
		}
		Fluid fluid = info.getFluid(stack);
		return fluid == ModFluids.CLEANING_SOLVENT.getFluid();
	}

	/**
	 * @return {@code int} ticks one cleaning cell currently needs
	 */
	public int cleaningDuration() {
		return Math.max(1, (int) Math.round(CLEANING_TICKS * (1.0 - getSpeedMultiplier())));
	}

	/**
	 * Applies one cleaning cell, if the output slot can hold the empty cell.
	 *
	 * @return {@code boolean} whether the cleaning step was performed
	 */
	private boolean applyCleaning() {
		ItemStack input = inventory.getStack(CLEANING_INPUT_SLOT);
		if (!isCleaningCell(input) || impurity <= 0) {
			return false;
		}
		ItemStack output = inventory.getStack(CLEANING_OUTPUT_SLOT);
		ItemStack empty = DynamicCellItem.getEmptyCell(1);
		if (!output.isEmpty() && !ItemStack.areItemsAndComponentsEqual(output, empty)) {
			return false;
		}
		if (output.getCount() >= output.getMaxCount()) {
			return false;
		}

		input.decrement(1);
		inventory.setStack(CLEANING_INPUT_SLOT, input.isEmpty() ? ItemStack.EMPTY : input);
		if (output.isEmpty()) {
			inventory.setStack(CLEANING_OUTPUT_SLOT, empty);
		} else {
			output.increment(1);
			inventory.setStack(CLEANING_OUTPUT_SLOT, output);
		}

		impurity = Math.max(0, impurity - CLEANING_AMOUNT);
		markDirty();
		return true;
	}

	// =======================================================================
	// tick
	// =======================================================================

	@Override
	public void tick(World world, BlockPos pos, BlockState state, MachineBaseBlockEntity blockEntity) {
		super.tick(world, pos, state, blockEntity);
		if (world == null || world.isClient) {
			return;
		}

		// Washing runs on its own timer: no energy, fixed duration, sped up by
		// overclockers like every other timed process.
		if (impurity <= 0 || !isMultiblockValid() || !isCleaningCell(inventory.getStack(CLEANING_INPUT_SLOT))) {
			cleaningTicks = 0;
			return;
		}
		cleaningTicks++;
		if (cleaningTicks >= cleaningDuration()) {
			if (applyCleaning()) {
				cleaningTicks = 0;
			} else {
				// Output is full: hold the progress instead of wasting the cell.
				cleaningTicks = cleaningDuration();
			}
		}
	}

	// =======================================================================
	// persistence / GUI
	// =======================================================================

	@Override
	public void readNbt(NbtCompound tagCompound, RegistryWrapper.WrapperLookup registryLookup) {
		super.readNbt(tagCompound, registryLookup);
		impurity = tagCompound.getInt("impurity");
	}

	@Override
	public void writeNbt(NbtCompound tagCompound, RegistryWrapper.WrapperLookup registryLookup) {
		super.writeNbt(tagCompound, registryLookup);
		tagCompound.putInt("impurity", impurity);
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
				// Cleaning solvent cell in, empty cell out
				.filterSlot(CLEANING_INPUT_SLOT, 89, 62, PrimitiveDistillationTowerBlockEntity::isCleaningCell)
				.outputSlot(CLEANING_OUTPUT_SLOT, 107, 62)
				.energySlot(10, 8, 72).syncEnergyValue().syncCrafterValue()
				.sync(PacketCodecs.VAR_INT, this::getImpurity, this::setImpurity)
				.addInventory().create(this, syncID);
	}

	@Override
	public boolean canCraft(RebornRecipe rebornRecipe) {
		return isMultiblockValid() && !isBlocked();
	}

	/**
	 * Crafter that refuses to pick a recipe while the tower is fouled, and
	 * deposits impurity after every completed run.
	 */
	private static final class FoulingCrafter extends ProxyRecipeCrafter {

		private final PrimitiveDistillationTowerBlockEntity tower;

		FoulingCrafter(PrimitiveDistillationTowerBlockEntity tower, RebornInventory<?> inventory,
				int[] inputs, int[] outputs) {
			super(ModRecipes.PRIMITIVE_DISTILLATION_TOWER, List.of(ModRecipes.DISTILLATION_TOWER),
					1.5F, 0.8F, tower, 4, 6, inventory, inputs, outputs);
			this.tower = tower;
		}

		@Override
		public void updateCurrentRecipe() {
			// Fouled: drop the recipe and stay idle instead of starting a run the
			// machine will refuse to finish.
			if (tower.isBlocked() || !tower.isMultiblockValid()) {
				currentRecipe = null;
				currentParallelCount = 1;
				currentNeededTicks = 0;
				setIsActive();
				return;
			}
			super.updateCurrentRecipe();
		}

		@Override
		public boolean canCraftAgain() {
			// Keeps the "active" state (and therefore the lit front) off while the
			// tower is fouled, instead of it flickering on every recipe check.
			return !tower.isBlocked() && super.canCraftAgain();
		}

		@Override
		protected void onCraftCompleted(RebornRecipe recipe) {
			if (tower.getWorld() != null) {
				tower.addImpurity(tower.getWorld());
			}
		}
	}
}
