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

package techreborn.blockentity.generator.multiblock;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import reborncore.common.util.Tank;
import reborncore.common.blockentity.MachineBaseBlockEntity;
import reborncore.common.crafting.RecipeUtils;
import reborncore.common.fluid.FluidUtils;
import reborncore.common.fluid.FluidValue;
import reborncore.common.screen.BuiltScreenHandler;
import reborncore.common.screen.BuiltScreenHandlerProvider;
import reborncore.common.screen.builder.ScreenHandlerBuilder;
import techreborn.blockentity.generator.GeneratorTooltipHelper;
import techreborn.config.TechRebornConfig;
import techreborn.init.ModFluids;
import techreborn.init.ModRecipes;
import techreborn.init.TRBlockEntities;
import techreborn.init.TRContent;
import techreborn.recipe.recipes.FluidGeneratorRecipe;

import java.util.List;

/**
 * Rocketdyne F-1A: a tier 3 multiblock rocket engine used as a generator.
 * <p>
 * <b>Propellant.</b> The engine drinks compressed air: one bucket every
 * {@value #PROPELLANT_CYCLE_TICKS} ticks while it runs. Cutting the air stops it
 * dead - no air, no output.
 * <p>
 * <b>Fuel.</b> Any fluid carrying a {@code techreborn:rocketdyne_engine} recipe
 * can be burned; the recipe's {@code power} is the fuel's calorific value in
 * EU/mB, in the same shape as every other generator recipe. Once a second the
 * engine tries to take one more bucket of fuel; succeeding renews the priming
 * for {@value #PRIMED_DURATION_TICKS} ticks, during which the output ceiling and
 * the fuel's calorific value are both doubled.
 * <p>
 * Priming rather than a fallback matters: without it a fuel interruption would
 * silently halve the output while the engine keeps running, which looks like the
 * engine simply being slow. Consuming a bucket to <em>raise</em> the ceiling
 * means the full 2x is something the player pays for and has to keep feeding.
 */
public class RocketdyneF1AEngineBlockEntity extends LargeFluidGeneratorBlockEntity implements BuiltScreenHandlerProvider {

	/** How often the engine drinks a bucket of compressed air. */
	public static final int PROPELLANT_CYCLE_TICKS = 100;

	/** How long one bucket of fuel keeps the engine primed. */
	public static final int PRIMED_DURATION_TICKS = 20;

	/** Ticks between attempts to take the next bucket of fuel. */
	private static final int FUEL_CHECK_INTERVAL_TICKS = 20;

	/** A whole bucket, used for both the air cycle and one fuel charge. */
	private static final FluidValue ONE_BUCKET = FluidValue.BUCKET;

	/** Primed output and calorific multiplier. */
	public static final int PRIMED_MULTIPLIER = 2;

	/**
	 * Tank indices. The fuel and propellant indices come from the generator base
	 * class; only the third tank is specific to this machine.
	 */
	public static final int TANK_LIQUID_HYDROGEN = 2;

	/**
	 * Capacity of the liquid hydrogen tank. The fuel and propellant tanks come from
	 * the generator base class, which sizes them itself.
	 */
	public static final FluidValue LIQUID_HYDROGEN_CAPACITY = FluidValue.BUCKET.multiply(16);

	/**
	 * Fuel tank (inherited from the generator base), the compressed air tank and
	 * the liquid hydrogen tank.
	 * <p>
	 * Three separate tanks rather than one shared one: the oxidiser must not mix
	 * with the fuel, and liquid hydrogen is a distinct priming charge with its own
	 * supply line. Splitting them also gives the per-tank fluid IO config
	 * something real to select between.
	 */
	public final Tank fuelTank;
	public final Tank liquidHydrogenTank;

	/** Ticks of priming left, synced for the GUI and Jade. */
	private int primedTicks = 0;

	/** Ticks until the next compressed air cycle is due. */
	private int propellantTicks = 0;

	/** Ticks until the next attempt to charge fuel. */
	private int fuelCheckTicks = 0;

	public RocketdyneF1AEngineBlockEntity(BlockPos pos, BlockState state) {
		super(TRBlockEntities.ROCKETDYNE_F1A_ENGINE, pos, state, ModRecipes.ROCKETDYNE_ENGINE,
				"RocketdyneF1AEngineBlockEntity", TechRebornConfig.rocketdyneF1AEngineEnergyPerTick);
		// The base class already built the fuel tank for us; reuse it as tank 0 so
		// the existing base-class fuel handling keeps pointing at the right one.
		this.fuelTank = tank;
		this.liquidHydrogenTank = new Tank("RocketdyneF1AEngineLiquidHydrogen", LIQUID_HYDROGEN_CAPACITY);
	}

	@Override
	public int getTankCount() {
		return 3;
	}

	@Override
	@Nullable
	public Tank getTank(int index) {
		return index == TANK_LIQUID_HYDROGEN ? liquidHydrogenTank : super.getTank(index);
	}

	/**
	 * The rocket engine burns compressed air as a hard requirement rather than as a
	 * damage boost, so the base class's boost must stay out of its air tank.
	 */
	@Override
	protected boolean usesPropellantBoost() {
		return false;
	}

	@Override
	public String getMultiblockId() {
		return "rocketdyne_f1a_engine";
	}

	@Override
	protected int getConfiguredMaxOutput() {
		return TechRebornConfig.rocketdyneF1AEngineMaxOutput;
	}

	@Override
	protected int getConfiguredMaxEnergy() {
		return TechRebornConfig.rocketdyneF1AEngineMaxEnergy;
	}

	// =======================================================================
	// combustion
	// =======================================================================

	/**
	 * @return {@code boolean} whether the engine holds compressed air and may
	 *         run at all
	 */
	public boolean hasPropellant() {
		return propellantTicks > 0;
	}

	/**
	 * @return {@code boolean} whether the ceiling and calorific value are doubled
	 */
	public boolean isPrimed() {
		return primedTicks > 0;
	}

	/**
	 * @return {@code int} ticks of priming left
	 */
	public int getPrimedTicks() {
		return primedTicks;
	}

	/**
	 * Client-side setter for the synced priming timer.
	 *
	 * @param primedTicks {@code int} ticks of priming left
	 */
	public void setPrimedTicks(int primedTicks) {
		this.primedTicks = primedTicks;
	}

	/**
	 * @return {@code float} the output multiplier currently in effect
	 */
	public float outputMultiplier() {
		return isPrimed() ? PRIMED_MULTIPLIER : 1.0F;
	}

	/**
	 * The base class turns the recipe's EU/mB into a per-tick draw from the
	 * output figure. Doubling the calorific value while primed therefore means
	 * twice the EU/t for the same bucket, which is exactly the spec.
	 */
	@Override
	protected int getEuPerTick() {
		return (int) (super.getEuPerTick() * outputMultiplier());
	}

	@Override
	public long getBaseMaxOutput() {
		return (long) (getConfiguredMaxOutput() * outputMultiplier());
	}

	/**
	 * Only burn fuel while the structure is assembled, the engine holds air and
	 * the buffer has room.
	 */
	@Override
	protected boolean tryAddingEnergy(int amount) {
		if (!isMultiblockValid() || !hasPropellant()) {
			return false;
		}
		return super.tryAddingEnergy(amount);
	}

	@Override
	public void tick(World world, BlockPos pos, BlockState state, MachineBaseBlockEntity blockEntity) {
		super.tick(world, pos, state, blockEntity);
		if (world == null || world.isClient || !isMultiblockValid()) {
			return;
		}

		if (primedTicks > 0) {
			primedTicks--;
		}
		if (propellantTicks > 0) {
			propellantTicks--;
		}

		// One bucket of air every five seconds, taken from the fuel tank so the
		// cell slot keeps working exactly like the other generators.
		if (--propellantTicks <= 0) {
			if (consumePropellant()) {
				propellantTicks = PROPELLANT_CYCLE_TICKS;
			} else {
				// Dry: shut down and drop the priming with it.
				propellantTicks = 0;
				primedTicks = 0;
			}
		}

		if (propellantTicks > 0 && --fuelCheckTicks <= 0) {
			fuelCheckTicks = FUEL_CHECK_INTERVAL_TICKS;
			chargeFuel();
		}

		if (world.getTime() % 20 == 0) {
			syncWithAll();
		}
	}

	/**
	 * Routes a cell to the tank that owns its fluid.
	 * <p>
	 * The base class only knows about one tank, which would send every cell into
	 * the fuel tank. Here compressed air lands in the propellant tank, hydrogen in
	 * the liquid hydrogen tank, and everything else in the fuel tank, matching what
	 * the machine actually reads while it runs.
	 */
	@Override
	protected void handleCellSlot() {
		ItemStack inputStack = inventory.getStack(0);
		if (inputStack.isEmpty()) {
			return;
		}

		// Drain side: pick the tank by the fluid the cell holds.
		if (FluidUtils.containsMatchingFluid(inputStack, f -> f.matchesType(ModFluids.COMPRESSED_AIR.getFluid()))) {
			FluidUtils.drainContainers(propellantTank, inventory, 0, 1);
			return;
		}
		if (FluidUtils.containsMatchingFluid(inputStack, f -> f.matchesType(ModFluids.HYDROGEN.getFluid()))) {
			FluidUtils.drainContainers(liquidHydrogenTank, inventory, 0, 1);
			return;
		}
		if (FluidUtils.containsMatchingFluid(inputStack, f -> getRecipeForFluid(f) != null)) {
			FluidUtils.drainContainers(fuelTank, inventory, 0, 1);
			return;
		}

		// Fill side: an empty cell takes whatever the fuel tank holds.
		FluidUtils.fillContainers(fuelTank, inventory, 0, 1);
	}

	/**
	 * Takes one bucket of compressed air out of the tank.
	 *
	 * @return {@code boolean} whether the bucket was available
	 */
	private boolean consumePropellant() {
		if (!propellantTank.getFluid().matchesType(ModFluids.COMPRESSED_AIR.getFluid())) {
			return false;
		}
		if (propellantTank.getFluidAmount().lessThan(ONE_BUCKET)) {
			return false;
		}
		propellantTank.modifyFluid(instance -> instance.subtractAmount(ONE_BUCKET));
		return true;
	}

	/**
	 * Tries to take a bucket of fuel and, on success, renews the priming.
	 * <p>
	 * The fuel is only taken when the tank holds <em>more</em> than that bucket,
	 * so the fuel currently being burned is never swallowed by the priming step.
	 */
	private void chargeFuel() {
		// Liquid hydrogen is the priming charge when the engine has it: one bucket
		// buys the same window as a bucket of the main fuel, which is what makes a
		// dedicated hydrogen line worth plumbing to the third tank.
		if (liquidHydrogenTank.getFluid().matchesType(ModFluids.HYDROGEN.getFluid())
				&& liquidHydrogenTank.getFluidAmount().moreThan(ONE_BUCKET)) {
			liquidHydrogenTank.modifyFluid(instance -> instance.subtractAmount(ONE_BUCKET));
			primedTicks = PRIMED_DURATION_TICKS;
			return;
		}
		Fluid fuel = fuelTank.getFluid();
		if (getRecipeForFluid(fuel) == null) {
			return;
		}
		if (fuelTank.getFluidAmount().lessThanOrEqual(ONE_BUCKET)) {
			return;
		}
		fuelTank.modifyFluid(instance -> instance.subtractAmount(ONE_BUCKET));
		primedTicks = PRIMED_DURATION_TICKS;
	}

	// =======================================================================
	// recipes
	// =======================================================================

	@Override
	public List<FluidGeneratorRecipe> getRecipes() {
		return RecipeUtils.getRecipes(world, ModRecipes.ROCKETDYNE_ENGINE);
	}

	@Override
	public void addInfo(List<Text> info, boolean isReal, boolean hasData) {
		super.addInfo(info, isReal, hasData);
		GeneratorTooltipHelper.addGenerationRate(info, getEuPerTick());
	}

	@Override
	public BuiltScreenHandler createScreenHandler(int syncID, final PlayerEntity player) {
		return new ScreenHandlerBuilder("rocketdynef1aengine").player(player.getInventory()).inventory().hotbar()
				.addInventory().blockEntity(this).slot(0, 25, 35).outputSlot(1, 25, 55).syncEnergyValue()
				.sync(PacketCodecs.INTEGER, this::getTicksSinceLastChange, this::setTicksSinceLastChange)
				.sync(PacketCodecs.INTEGER, this::getPrimedTicks, this::setPrimedTicks)
				.sync(FluidValue.PACKET_CODEC, this::getTankAmount, this::setTankAmount)
				.sync(tank)
				.addInventory().create(this, syncID);
	}

	@Override
	public void writeNbt(NbtCompound tagCompound, RegistryWrapper.WrapperLookup registryLookup) {
		super.writeNbt(tagCompound, registryLookup);
		tagCompound.put("liquidHydrogenTank", tankToNbt(liquidHydrogenTank, registryLookup));
	}

	@Override
	public void readNbt(NbtCompound tagCompound, RegistryWrapper.WrapperLookup registryLookup) {
		super.readNbt(tagCompound, registryLookup);
		liquidHydrogenTank.read(tagCompound.getCompound("liquidHydrogenTank"), registryLookup);
	}

	/**
	 * {@link Tank#write} writes in place, so serialize through a scratch tag.
	 */
	private static NbtCompound tankToNbt(Tank tank, RegistryWrapper.WrapperLookup registryLookup) {
		NbtCompound scratch = new NbtCompound();
		tank.write(scratch, registryLookup);
		return scratch;
	}

	@Override
	public ItemStack getToolDrop(PlayerEntity entityPlayer) {
		return TRContent.Machine.ROCKETDYNE_F1A_ENGINE.getStack();
	}
}
