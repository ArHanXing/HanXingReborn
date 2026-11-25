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
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import reborncore.common.blockentity.MachineBaseBlockEntity;
import reborncore.common.crafting.RebornRecipe;
import reborncore.common.fluid.FluidUtils;
import reborncore.common.fluid.FluidValue;
import reborncore.common.fluid.container.FluidInstance;
import reborncore.common.screen.BuiltScreenHandler;
import reborncore.common.screen.BuiltScreenHandlerProvider;
import reborncore.common.screen.builder.ScreenHandlerBuilder;
import reborncore.common.util.RebornInventory;
import reborncore.common.util.Tank;
import techreborn.config.TechRebornConfig;
import techreborn.init.TRBlockEntities;
import techreborn.init.TRContent;
import techreborn.recipe.HeatSinkRecipeCrafter;
import techreborn.recipe.recipes.LargeHeatSinkRecipe;

/**
 * The Large Heat Sink: a tier 3 multiblock that runs its own recipes.
 * <p>
 * Not a bigger vacuum freezer. It carries its own {@link LargeHeatSinkRecipe}
 * type, so what a recipe consumes and produces - including which coolant it
 * needs - is decided by the recipe rather than inherited: nothing here hard-codes
 * liquid helium, or any other fluid.
 * <p>
 * The IO shape mirrors the industrial grinder: one liquid cell slot that can be
 * drained into and filled from the input tank, item inputs, item outputs, a
 * battery, and a separate product tank that pipes may drain but never fill.
 */
public class LargeHeatSinkBlockEntity extends JsonMultiblockMachineBlockEntity implements BuiltScreenHandlerProvider {

	private static final String MULTIBLOCK_ID = "large_heat_sink";

	/** Slot layout: liquid cell, item inputs, item outputs, battery. */
	private static final int CELL_SLOT = 0;
	private static final int[] INPUT_SLOTS = new int[]{1, 2};
	private static final int[] OUTPUT_SLOTS = new int[]{3, 4, 5, 6};
	/** Where a drained cell is returned, next to the cell slot. */
	private static final int CELL_OUTPUT_SLOT = 7;
	private static final int ENERGY_SLOT = 8;

	/** Tank size, 256 buckets, matching the industrial grinder. */
	public static final FluidValue TANK_CAPACITY = FluidValue.BUCKET.multiply(256);

	public final Tank tank;
	public final Tank outputTank;

	private int ticksSinceLastChange;

	public LargeHeatSinkBlockEntity(BlockPos pos, BlockState state) {
		super(TRBlockEntities.LARGE_HEAT_SINK, pos, state, "LargeHeatSink",
				TechRebornConfig.largeHeatSinkMaxInput,
				TechRebornConfig.largeHeatSinkMaxEnergy,
				TRContent.Machine.LARGE_HEAT_SINK.block, ENERGY_SLOT);
		this.inventory = new RebornInventory<>(ENERGY_SLOT + 1, "LargeHeatSinkBlockEntity", 64, this);
		this.crafter = new HeatSinkRecipeCrafter(this, this.inventory, INPUT_SLOTS, OUTPUT_SLOTS);
		this.crafter.setMaxParallel(32);
		this.tank = new Tank("LargeHeatSinkBlockEntity", TANK_CAPACITY);
		this.outputTank = new Tank("LargeHeatSinkOutput", TANK_CAPACITY);
	}

	@Override
	public String getMultiblockId() {
		return MULTIBLOCK_ID;
	}

	@Override
	public boolean canCraft(RebornRecipe rebornRecipe) {
		return isMultiblockValid();
	}

	// =======================================================================
	// tanks
	// =======================================================================

	@Nullable
	@Override
	public Tank getTank() {
		return tank;
	}

	@Override
	public Tank getTank(int index) {
		return switch (index) {
			case 0 -> tank;
			case 1 -> outputTank;
			default -> null;
		};
	}

	@Override
	public int getTankCount() {
		return 2;
	}

	/**
	 * The product tank is an output: pipes may drain it but never fill it, so a
	 * stray connection cannot poison a batch with the wrong fluid.
	 */
	@Override
	public boolean canInsertFluid(int tankIndex) {
		return tankIndex == 0;
	}

	// =======================================================================
	// recipe fluid handling
	// =======================================================================

	/**
	 * @param recipe {@link LargeHeatSinkRecipe} the recipe
	 * @param runs   {@code int} how many runs must be supported
	 * @return {@code boolean} whether the input tank holds enough of the coolant
	 */
	public boolean canConsumeCoolant(LargeHeatSinkRecipe recipe, int runs) {
		FluidInstance coolant = recipe.fluid();
		if (coolant.isEmpty()) {
			return true;
		}
		if (!tank.getFluid().matchesType(coolant.fluid())) {
			return false;
		}
		return tank.getFluidAmount().equalOrMoreThan(coolant.getAmount().multiply(Math.max(runs, 1)));
	}

	/**
	 * @param recipe {@link LargeHeatSinkRecipe} the recipe
	 * @param runs   {@code int} how many runs are finishing
	 */
	public void consumeCoolant(LargeHeatSinkRecipe recipe, int runs) {
		FluidInstance coolant = recipe.fluid();
		if (coolant.isEmpty()) {
			return;
		}
		tank.modifyFluid(current -> current.subtractAmount(coolant.getAmount().multiply(Math.max(runs, 1))));
	}

	/**
	 * @param recipe {@link LargeHeatSinkRecipe} the recipe
	 * @param runs   {@code int} how many runs must fit
	 * @return {@code boolean} whether the product tank can take {@code runs} outputs
	 */
	public boolean hasRoomForProduct(LargeHeatSinkRecipe recipe, int runs) {
		FluidInstance product = recipe.fluidOutput();
		if (product.isEmpty()) {
			return true;
		}
		if (!outputTank.isEmpty() && !outputTank.getFluid().matchesType(product.fluid())) {
			return false;
		}
		return outputTank.getFreeSpace().equalOrMoreThan(product.getAmount().multiply(Math.max(runs, 1)));
	}

	/**
	 * @param recipe {@link LargeHeatSinkRecipe} the recipe
	 * @param runs   {@code int} how many runs are finishing
	 */
	public void depositProduct(LargeHeatSinkRecipe recipe, int runs) {
		FluidInstance product = recipe.fluidOutput();
		if (product.isEmpty()) {
			return;
		}
		FluidValue amount = product.getAmount().multiply(Math.max(runs, 1));
		if (outputTank.isEmpty()) {
			outputTank.setFluidInstance(product.withAmount(amount));
		} else {
			outputTank.modifyFluid(current -> current.addAmount(amount));
		}
	}

	/**
	 * @param recipe {@link LargeHeatSinkRecipe} the recipe
	 * @return {@code int} how many parallel runs the tanks allow, {@code 0} for none
	 */
	public int parallelLimitFor(LargeHeatSinkRecipe recipe) {
		int limit = Integer.MAX_VALUE;

		FluidInstance coolant = recipe.fluid();
		if (!coolant.isEmpty()) {
			if (!tank.getFluid().matchesType(coolant.fluid())) {
				return 0;
			}
			limit = (int) Math.min(limit, tank.getFluidAmount().getRawValue() / coolant.getAmount().getRawValue());
		}

		FluidInstance product = recipe.fluidOutput();
		if (!product.isEmpty()) {
			if (!outputTank.isEmpty() && !outputTank.getFluid().matchesType(product.fluid())) {
				return 0;
			}
			limit = (int) Math.min(limit,
				outputTank.getFreeSpace().getRawValue() / product.getAmount().getRawValue());
		}

		return limit;
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

		ticksSinceLastChange++;
		if (ticksSinceLastChange >= cellTransferInterval()) {
			ticksSinceLastChange = 0;
			if (!inventory.getStack(CELL_SLOT).isEmpty()) {
				FluidUtils.drainContainers(tank, inventory, CELL_SLOT, CELL_OUTPUT_SLOT);
				FluidUtils.fillContainers(tank, inventory, CELL_SLOT, CELL_OUTPUT_SLOT);
			}
		}
	}

	// =======================================================================
	// persistence
	// =======================================================================

	@Override
	public void readNbt(NbtCompound tagCompound, RegistryWrapper.WrapperLookup registryLookup) {
		super.readNbt(tagCompound, registryLookup);
		tank.read(tagCompound, registryLookup);
		outputTank.read(tagCompound, registryLookup);
		ticksSinceLastChange = tagCompound.getInt("ticksSinceLastChange");
	}

	@Override
	public void writeNbt(NbtCompound tagCompound, RegistryWrapper.WrapperLookup registryLookup) {
		super.writeNbt(tagCompound, registryLookup);
		tank.write(tagCompound, registryLookup);
		outputTank.write(tagCompound, registryLookup);
		tagCompound.putInt("ticksSinceLastChange", ticksSinceLastChange);
	}

	// =======================================================================
	// GUI
	// =======================================================================

	@Override
	public BuiltScreenHandler createScreenHandler(int syncID, final PlayerEntity player) {
		return new ScreenHandlerBuilder("largeheatsink").player(player.getInventory()).inventory().hotbar().addInventory()
			.blockEntity(this)
			.fluidSlot(CELL_SLOT, 34, 35).slot(INPUT_SLOTS[0], 84, 43).outputSlot(OUTPUT_SLOTS[0], 126, 18)
			.outputSlot(OUTPUT_SLOTS[1], 126, 36).outputSlot(OUTPUT_SLOTS[2], 126, 54).outputSlot(OUTPUT_SLOTS[3], 126, 72)
			.outputSlot(CELL_OUTPUT_SLOT, 34, 55).energySlot(ENERGY_SLOT, 8, 72)
			.sync(tank).sync(outputTank).syncEnergyValue().syncCrafterValue().addInventory().create(this, syncID);
	}
}
