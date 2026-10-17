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
import reborncore.common.fluid.FluidUtils;
import reborncore.common.fluid.FluidValue;
import reborncore.common.fluid.container.FluidInstance;
import reborncore.common.recipes.RecipeCrafter;
import reborncore.common.screen.BuiltScreenHandler;
import reborncore.common.screen.BuiltScreenHandlerProvider;
import reborncore.common.screen.builder.ScreenHandlerBuilder;
import reborncore.common.util.RebornInventory;
import reborncore.common.util.Tank;
import techreborn.config.TechRebornConfig;
import techreborn.init.ModRecipes;
import techreborn.init.TRBlockEntities;
import techreborn.init.TRContent;
import techreborn.recipe.recipes.GasCollectorRecipe;

/**
 * The Gas Collector: a multiblock that condenses the fluid of the dimension it
 * stands in.
 * <p>
 * Mechanically it is the Void Drill without the reserve: the recipe is picked by
 * dimension, it is neither depleted nor limited per chunk, and the only things
 * that stop it are a broken structure, a full tank or the absence of a recipe
 * for the current dimension.
 */
public class GasCollectorBlockEntity extends JsonMultiblockMachineBlockEntity implements BuiltScreenHandlerProvider {

	private static final String MULTIBLOCK_ID = "gas_collector";

	/** Slot layout: containers in, filled containers out, battery. */
	private static final int CONTAINER_INPUT_SLOT = 0;
	private static final int CONTAINER_OUTPUT_SLOT = 1;
	private static final int ENERGY_SLOT = 2;

	/** Tank size, 128 buckets. */
	public static final FluidValue TANK_CAPACITY = FluidValue.BUCKET.multiply(128);

	public final Tank tank;
	private int ticksSinceLastChange;

	public GasCollectorBlockEntity(BlockPos pos, BlockState state) {
		super(TRBlockEntities.GAS_COLLECTOR, pos, state, "GasCollector",
				TechRebornConfig.gasCollectorMaxInput,
				TechRebornConfig.gasCollectorMaxEnergy,
				TRContent.Machine.GAS_COLLECTOR.block, ENERGY_SLOT);
		this.inventory = new RebornInventory<>(ENERGY_SLOT + 1, "GasCollectorBlockEntity", 64, this);
		// No item inputs and no item outputs: the run is resolved by the recipe's
		// onCraft hook, which pumps fluid into the tank.
		this.crafter = new RecipeCrafter(ModRecipes.GAS_COLLECTOR, this, 0, 0, this.inventory, new int[0], new int[0]);
		this.tank = new Tank("GasCollectorBlockEntity", TANK_CAPACITY);
	}

	@Override
	public String getMultiblockId() {
		return MULTIBLOCK_ID;
	}

	// =======================================================================
	// recipe gating
	// =======================================================================

	/**
	 * @param recipe {@link GasCollectorRecipe} the candidate recipe
	 * @return {@code boolean} whether the controller's dimension matches
	 */
	public boolean matchesDimension(GasCollectorRecipe recipe) {
		World world = getWorld();
		if (world == null) {
			return false;
		}
		return world.getRegistryKey().getValue().equals(recipe.dimension());
	}

	/**
	 * @param recipe {@link GasCollectorRecipe} the candidate recipe
	 * @return {@code boolean} whether a run could start right now
	 */
	public boolean canRun(GasCollectorRecipe recipe) {
		if (!isMultiblockValid() || !matchesDimension(recipe)) {
			return false;
		}
		return tank.canFit(recipe.fluid(), FluidValue.fromMillibuckets(recipe.amountMb()));
	}

	/**
	 * Resolves a finished run: pumps the fluid into the tank.
	 *
	 * @param recipe {@link GasCollectorRecipe} the run that finished
	 * @return {@code boolean} whether it produced anything; {@code false} keeps
	 *         the run alive so it retries once there is tank space again
	 */
	public boolean completeCollect(GasCollectorRecipe recipe) {
		FluidValue amount = FluidValue.fromMillibuckets(recipe.amountMb());
		if (!tank.canFit(recipe.fluid(), amount)) {
			return false;
		}
		tank.modifyFluid(instance -> instance.isEmptyFluid()
				? new FluidInstance(recipe.fluid(), amount)
				: instance.addAmount(amount));
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

		ticksSinceLastChange++;
		// Check cells twice per second, like the fluid replicator.
		if (ticksSinceLastChange >= 10) {
			if (!inventory.getStack(CONTAINER_INPUT_SLOT).isEmpty()) {
				FluidUtils.fillContainers(tank, inventory, CONTAINER_INPUT_SLOT, CONTAINER_OUTPUT_SLOT);
			}
			ticksSinceLastChange = 0;
		}
	}

	// =======================================================================
	// persistence / GUI
	// =======================================================================

	@Override
	public void readNbt(NbtCompound tagCompound, RegistryWrapper.WrapperLookup registryLookup) {
		super.readNbt(tagCompound, registryLookup);
		tank.read(tagCompound, registryLookup);
	}

	@Override
	public void writeNbt(NbtCompound tagCompound, RegistryWrapper.WrapperLookup registryLookup) {
		super.writeNbt(tagCompound, registryLookup);
		tank.write(tagCompound, registryLookup);
	}

	@Nullable
	@Override
	public Tank getTank() {
		return tank;
	}

	@Override
	public BuiltScreenHandler createScreenHandler(int syncID, final PlayerEntity player) {
		return new ScreenHandlerBuilder("gascollector").player(player.getInventory()).inventory().hotbar().addInventory()
				.blockEntity(this)
				.fluidSlot(CONTAINER_INPUT_SLOT, 124, 35)
				.outputSlot(CONTAINER_OUTPUT_SLOT, 124, 55)
				.energySlot(ENERGY_SLOT, 8, 72)
				.sync(tank).syncEnergyValue().syncCrafterValue()
				.addInventory().create(this, syncID);
	}
}
