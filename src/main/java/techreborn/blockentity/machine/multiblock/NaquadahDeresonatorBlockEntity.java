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
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import reborncore.common.blockentity.MachineBaseBlockEntity;
import reborncore.common.crafting.RebornRecipe;
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
import techreborn.recipe.NaquadahDeresonatorCrafter;
import techreborn.recipe.recipes.NaquadahDeresonatorRecipe;

/**
 * The Naquadah Deresonator: a tier 2 multiblock that resolves a recipe with a
 * single chance roll instead of producing deterministically.
 * <p>
 * <b>The gamble.</b> A run always costs its inputs and its energy, but only
 * yields the outputs when the roll succeeds. The chance starts at
 * {@value #BASE_SUCCESS_RATE} and grows by {@value #SUCCESS_PER_UNIT} for every
 * {@value #FLUID_PER_UNIT_MB} mB of the recipe's fluid the machine drains while
 * the run is in progress. The tank is emptied onto the roll every
 * {@value #DRAIN_INTERVAL_TICKS} ticks, in full, so feeding a steady stream is
 * what keeps the chance climbing.
 * <p>
 * <b>Exact feeding.</b> A partial unit is heavily punished: when the total is
 * not a whole multiple of {@value #FLUID_PER_UNIT_MB} mB the chance is
 * multiplied by {@code 2^(-10 * waste)}, where {@code waste} is the leftover
 * fraction of everything that was fed. Feeding 15 B therefore yields a chance of
 * {@code (0.10 + 0.10) * 2^(-10/3)}, while feeding exactly 10 B or 20 B does
 * not.
 * <p>
 * <b>The Nether.</b> The machine is tuned for the Nether. Anywhere else it
 * takes {@value #OFF_DIMENSION_TIME_MULTIPLIER}x as long but draws only
 * {@value #OFF_DIMENSION_ENERGY_MULTIPLIER}x the power, so a run costs roughly
 * the same energy for less throughput.
 */
public class NaquadahDeresonatorBlockEntity extends JsonMultiblockMachineBlockEntity implements BuiltScreenHandlerProvider {

	private static final String MULTIBLOCK_ID = "naquadah_deresonator";

	/** Slot layout: two inputs, four outputs, one battery. */
	private static final int INPUT_SLOT_A = 0;
	private static final int INPUT_SLOT_B = 1;
	private static final int[] OUTPUT_SLOTS = new int[]{2, 3, 4, 5};
	private static final int ENERGY_SLOT = 6;

	/** Tank size, 100 buckets. */
	public static final FluidValue TANK_CAPACITY = FluidValue.BUCKET.multiply(100);

	/** Fluid worth one success step, 10 buckets. */
	public static final long FLUID_PER_UNIT_MB = 10_000L;

	/**
	 * Raw fluid units per millibucket. Fabric counts a bucket as 81000 units,
	 * while {@code FluidValue.fromMillibuckets} multiplies by 81.
	 */
	private static final long RAW_PER_MB = 81L;

	/** Success chance of a run that consumed no fluid at all. */
	public static final double BASE_SUCCESS_RATE = 0.10;

	/** Chance added per full {@link #FLUID_PER_UNIT_MB} of fluid consumed. */
	public static final double SUCCESS_PER_UNIT = 0.10;

	/**
	 * Exponent scale of the partial-unit penalty: the chance is multiplied by
	 * {@code 2^(-PENALTY_SCALE * wasteRatio)}.
	 */
	public static final double PENALTY_SCALE = 10.0;

	/** The tank is drained in full every this many processing ticks. */
	public static final int DRAIN_INTERVAL_TICKS = 5;

	/** Process time and power multipliers applied outside the Nether. */
	public static final double OFF_DIMENSION_TIME_MULTIPLIER = 1.5;
	public static final double OFF_DIMENSION_ENERGY_MULTIPLIER = 0.8;

	public final Tank tank;

	/**
	 * Fluid consumed by the run in progress, in millibuckets. Reset whenever a
	 * run starts, and read by {@link #rollCraftSuccess()} when it ends.
	 */
	private long runFluidMb = 0;

	/** Next {@code crafter.currentTickTime} value at which to drain the tank. */
	private int nextDrainTick = DRAIN_INTERVAL_TICKS;

	public NaquadahDeresonatorBlockEntity(BlockPos pos, BlockState state) {
		super(TRBlockEntities.NAQUADAH_DERESONATOR, pos, state, "NaquadahDeresonator",
				TechRebornConfig.naquadahDeresonatorMaxInput,
				TechRebornConfig.naquadahDeresonatorMaxEnergy,
				TRContent.Machine.NAQUADAH_DERESONATOR.block, ENERGY_SLOT);
		this.inventory = new RebornInventory<>(ENERGY_SLOT + 1, "NaquadahDeresonatorBlockEntity", 64, this);
		this.crafter = new NaquadahDeresonatorCrafter(this, this.inventory,
				new int[]{INPUT_SLOT_A, INPUT_SLOT_B}, OUTPUT_SLOTS);
		this.tank = new Tank("NaquadahDeresonatorBlockEntity", TANK_CAPACITY);
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
	// dimension
	// =======================================================================

	/**
	 * @return {@code boolean} whether the machine sits in the Nether
	 */
	public boolean isInNether() {
		World world = getWorld();
		return world != null && world.getRegistryKey() == World.NETHER;
	}

	/**
	 * @return {@code double} the process-time multiplier of the current
	 *         dimension ({@code 1.0} in the Nether)
	 */
	public double dimensionTimeMultiplier() {
		return isInNether() ? 1.0 : OFF_DIMENSION_TIME_MULTIPLIER;
	}

	/**
	 * @return {@code double} the power multiplier of the current dimension
	 *         ({@code 1.0} in the Nether)
	 */
	public double dimensionEnergyMultiplier() {
		return isInNether() ? 1.0 : OFF_DIMENSION_ENERGY_MULTIPLIER;
	}

	/**
	 * The crafter prices a tick through {@code IUpgradeHandler.getEuPerTick},
	 * whose base implementation only knows about the upgrade power multiplier.
	 * Overriding it is what makes the dimension discount reach the draw.
	 *
	 * @param baseEu {@code long} the unmodified energy per tick
	 * @return {@code long} the energy actually drawn this tick
	 */
	@Override
	public long getEuPerTick(long baseEu) {
		return (long) (baseEu * dimensionEnergyMultiplier() * getPowerMultiplier());
	}

	// =======================================================================
	// success chance
	// =======================================================================

	/**
	 * Converts the fluid a run consumed into its success chance.
	 * <p>
	 * Every whole {@link #FLUID_PER_UNIT_MB} adds {@link #SUCCESS_PER_UNIT} on
	 * top of {@link #BASE_SUCCESS_RATE}. A leftover that does not fill a whole
	 * unit multiplies the result by {@code 2^(-PENALTY_SCALE * waste)}, where
	 * {@code waste} is the leftover share of the total fed, so partial units are
	 * much worse than either rounding down or saving up.
	 * <p>
	 * The result is deliberately not clamped: a full 100 B tank already reaches
	 * 110%, and the roll simply always succeeds from there.
	 *
	 * @param fluidMb {@code long} fluid consumed by the run, in millibuckets
	 * @return {@code double} the chance the run succeeds
	 */
	public static double successRate(long fluidMb) {
		if (fluidMb <= 0) {
			return BASE_SUCCESS_RATE;
		}
		long units = fluidMb / FLUID_PER_UNIT_MB;
		long remainder = fluidMb % FLUID_PER_UNIT_MB;
		double rate = BASE_SUCCESS_RATE + SUCCESS_PER_UNIT * units;
		if (remainder > 0) {
			double waste = (double) remainder / (double) fluidMb;
			rate *= Math.pow(2.0, -PENALTY_SCALE * waste);
		}
		return rate;
	}

	/**
	 * @return {@code double} the success chance of the run in progress
	 */
	public double getCurrentSuccessRate() {
		return successRate(runFluidMb);
	}

	/**
	 * @return {@code long} fluid consumed by the run in progress, in
	 *         millibuckets
	 */
	public long getRunFluidMb() {
		return runFluidMb;
	}

	/**
	 * Client-side setter for the synced run fluid. The client only displays it.
	 *
	 * @param runFluidMb {@code long} fluid consumed by the run in progress
	 */
	public void setRunFluidMb(long runFluidMb) {
		this.runFluidMb = runFluidMb;
	}

	/**
	 * Resolves the run that just finished. Called by the crafter once the
	 * processing time has elapsed.
	 *
	 * @return {@code boolean} whether the run produces its outputs
	 */
	public boolean rollCraftSuccess() {
		World world = getWorld();
		if (world == null) {
			return false;
		}
		return world.random.nextDouble() < getCurrentSuccessRate();
	}

	// =======================================================================
	// tick
	// =======================================================================

	@Override
	public void tick(World world, BlockPos pos, BlockState state, MachineBaseBlockEntity blockEntity) {
		super.tick(world, pos, state, blockEntity);
		if (world == null || world.isClient || crafter == null) {
			return;
		}

		RebornRecipe current = crafter.currentRecipe;
		if (current == null) {
			resetRun();
			return;
		}

		int tickTime = crafter.currentTickTime;
		if (tickTime == 0) {
			// A run begins (or the previous one finished) with the counter at
			// zero, which is the only moment the accumulated fluid is cleared.
			resetRun();
		} else if (tickTime < crafter.currentNeededTicks && tickTime >= nextDrainTick) {
			drainIntoRun(current);
			nextDrainTick = tickTime + DRAIN_INTERVAL_TICKS;
		}

		// The standard formula only knows about overclocker upgrades, so the
		// dimension penalty is folded in here.
		int needed = effectiveTime(current.time());
		if (crafter.currentNeededTicks != needed) {
			crafter.currentNeededTicks = needed;
		}
	}

	/**
	 * @param recipeTime {@code int} the recipe's base process time
	 * @return {@code int} the process time after the dimension penalty and the
	 *         speed upgrades
	 */
	public int effectiveTime(int recipeTime) {
		double multiplier = dimensionTimeMultiplier() * (1.0 - getSpeedMultiplier());
		return Math.max(1, (int) Math.round(recipeTime * multiplier));
	}

	private void resetRun() {
		runFluidMb = 0;
		nextDrainTick = DRAIN_INTERVAL_TICKS;
	}

	/**
	 * Empties the tank into the run's tally. Only the recipe's own fluid counts;
	 * a tank holding anything else is left alone rather than voided.
	 *
	 * @param recipe {@link RebornRecipe} the run in progress
	 */
	private void drainIntoRun(RebornRecipe recipe) {
		if (!(recipe instanceof NaquadahDeresonatorRecipe deresonatorRecipe) || tank.isEmpty()) {
			return;
		}
		FluidInstance instance = tank.getFluidInstance();
		if (!instance.fluid().matchesType(deresonatorRecipe.getFluid())) {
			return;
		}
		runFluidMb += instance.getAmount().getRawValue() / RAW_PER_MB;
		tank.setFluidInstance(FluidInstance.EMPTY);
	}

	// =======================================================================
	// persistence
	// =======================================================================

	@Override
	public void readNbt(NbtCompound tagCompound, RegistryWrapper.WrapperLookup registryLookup) {
		super.readNbt(tagCompound, registryLookup);
		tank.read(tagCompound, registryLookup);
		runFluidMb = tagCompound.getLong("runFluidMb");
	}

	@Override
	public void writeNbt(NbtCompound tagCompound, RegistryWrapper.WrapperLookup registryLookup) {
		super.writeNbt(tagCompound, registryLookup);
		tank.write(tagCompound, registryLookup);
		tagCompound.putLong("runFluidMb", runFluidMb);
	}

	@Nullable
	@Override
	public Tank getTank() {
		return tank;
	}

	// =======================================================================
	// GUI
	// =======================================================================

	@Override
	public BuiltScreenHandler createScreenHandler(int syncID, final PlayerEntity player) {
		return new ScreenHandlerBuilder("naquadahderesonator").player(player.getInventory()).inventory().hotbar().addInventory()
				.blockEntity(this)
				.slot(INPUT_SLOT_A, 35, 27).slot(INPUT_SLOT_B, 35, 47)
				.outputSlot(2, 107, 27).outputSlot(3, 125, 27).outputSlot(4, 107, 47).outputSlot(5, 125, 47)
				.energySlot(ENERGY_SLOT, 8, 72)
				.syncEnergyValue().syncCrafterValue()
				.sync(PacketCodecs.VAR_LONG, this::getRunFluidMb, this::setRunFluidMb)
				.sync(tank)
				.addInventory().create(this, syncID);
	}
}
