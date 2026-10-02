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

import java.util.List;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import reborncore.common.blockentity.MachineBaseBlockEntity;
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
import techreborn.recipe.recipes.IsotopeSeparatorRecipe;

/**
 * The Isotope Separator: a tier 2 multiblock that separates a feed into a
 * product and a tails stream, where the separation quality trades off against
 * speed.
 * <p>
 * <b>Speed.</b> The rotation speed {@code r} comes from the redstone signal
 * applied to the controller itself (0..15 scaled to 0..1) and is capped by the
 * rotor in slot 1. A missing rotor still runs, at a 25% floor.
 * <p>
 * <b>The trade-off.</b> Faster rotation shortens the process but costs more
 * energy, and past {@code r = 0.6} the separation degrades: {@code outputs[0]}
 * is replaced by the recipe's {@code degraded_output}, i.e. the machine needs
 * one more cascade stage for the same enrichment. The sweet spot is therefore
 * exactly {@code r = 0.6}, and time-accelerating upgrades can only make the
 * machine burn more power, never bypass the cascade.
 * <p>
 * Rotors wear out while the machine actually processes, faster at higher speed.
 */
public class IsotopeSeparatorBlockEntity extends JsonMultiblockMachineBlockEntity implements BuiltScreenHandlerProvider {

	private static final String MULTIBLOCK_ID = "isotope_separator";

	/** Slot layout. */
	private static final int INPUT_SLOT = 0;
	private static final int ROTOR_SLOT = 1;
	private static final int PRODUCT_SLOT = 2;
	private static final int TAILS_SLOT = 3;
	private static final int ENERGY_SLOT = 4;

	/** Speed used when no rotor is installed. */
	public static final double NO_ROTOR_RPM = 0.25;
	/** Rotation speed above which the separation degrades. */
	public static final double DEGRADE_THRESHOLD = 0.6;

	private double rotorEnergyFactor = 1.0;
	/** Last computed values, kept for the GUI. Recomputed every tick. */
	private int displaySignal = 0;
	private double displayRpm = 0;
	private double displayRpmLimit = NO_ROTOR_RPM;
	private boolean displayDegraded = false;

	public IsotopeSeparatorBlockEntity(BlockPos pos, BlockState state) {
		super(TRBlockEntities.ISOTOPE_SEPARATOR, pos, state, "IsotopeSeparator",
				TechRebornConfig.isotopeSeparatorMaxInput,
				TechRebornConfig.isotopeSeparatorMaxEnergy,
				TRContent.Machine.ISOTOPE_SEPARATOR.block, ENERGY_SLOT);
		final int[] inputs = new int[]{INPUT_SLOT, ROTOR_SLOT};
		final int[] outputs = new int[]{PRODUCT_SLOT, TAILS_SLOT};
		this.inventory = new RebornInventory<>(ENERGY_SLOT + 1, "IsotopeSeparatorBlockEntity", 64, this);
		this.crafter = new IsotopeSeparatorCrafter(ModRecipes.ISOTOPE_SEPARATOR, this, 1, 2,
				this.inventory, inputs, outputs, this);
	}

	@Override
	public String getMultiblockId() {
		return MULTIBLOCK_ID;
	}

	// =======================================================================
	// rotation speed
	// =======================================================================

	/**
	 * @return {@code int} the redstone signal reaching the controller (0..15)
	 */
	public int getRedstoneSignal() {
		World world = getWorld();
		if (world == null) {
			return 0;
		}
		return world.getReceivedRedstonePower(getPos());
	}

	/**
	 * @return {@code double} the rotor's speed cap, or the no-rotor floor
	 */
	public double getRotorRpmLimit() {
		TRContent.Parts rotor = TRContent.Parts.rotorFromStack(inventory.getStack(ROTOR_SLOT));
		return rotor == null || rotor.rpmLimit == null ? NO_ROTOR_RPM : rotor.rpmLimit;
	}

	/**
	 * @return {@code double} the effective rotation speed (0..1)
	 */
	public double getRpm() {
		double limit = getRotorRpmLimit();
		return Math.min(getRedstoneSignal() / 15.0, limit);
	}

	/**
	 * @return {@code boolean} {@code true} when running fast enough to lose a
	 *         separation grade
	 */
	public boolean isDegraded() {
		return getRpm() > DEGRADE_THRESHOLD;
	}

	/**
	 * @param rpm {@code double} the effective rotation speed
	 * @return {@code int} the process time for the given speed
	 */
	public int effectiveTime(int recipeTime, double rpm) {
		double factor = 1.0 - TechRebornConfig.isotopeSeparatorTimeReduction * rpm;
		return (int) Math.max(1, Math.round(recipeTime * factor));
	}

	/**
	 * @param rpm {@code double} the effective rotation speed
	 * @return {@code double} the energy multiplier for the given speed
	 */
	public double energyMultiplier(double rpm) {
		double base = 1.0 + TechRebornConfig.isotopeSeparatorEnergyPenalty * rpm;
		TRContent.Parts rotor = TRContent.Parts.rotorFromStack(inventory.getStack(ROTOR_SLOT));
		return base * (rotor == null ? 1.0 : rotor.rotorEnergyFactor);
	}

	// =======================================================================
	// energy
	// =======================================================================

	/**
	 * {@code RecipeCrafter} prices a tick at {@code currentRecipe.power() *
	 * getPowerMultiplier()}, so the rotation cost is folded in here rather than
	 * in {@code getEuPerTick} (which the crafter does not call).
	 */
	@Override
	public double getPowerMultiplier() {
		return super.getPowerMultiplier() * rotorEnergyFactor;
	}

	/**
	 * Runs after the upgrade pipeline has reset and re-applied the generic
	 * multipliers, which is the only place where writing {@link
	 * #rotorEnergyFactor} survives to the crafter.
	 */
	@Override
	protected void afterUpgradesApplication() {
		rotorEnergyFactor = energyMultiplier(getRpm());
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

		double rpm = getRpm();
		displaySignal = getRedstoneSignal();
		displayRpm = rpm;
		displayRpmLimit = getRotorRpmLimit();
		displayDegraded = rpm > DEGRADE_THRESHOLD;

		// Shorter process at higher speed. The overclocker upgrade also writes
		// currentNeededTicks, so take the faster of the two instead of letting
		// them multiply (spec: speed upgrades must not sidestep the cascade).
		RebornRecipe current = crafter == null ? null : crafter.currentRecipe;
		if (current != null) {
			int byRpm = effectiveTime(current.time(), rpm);
			int byUpgrade = Math.max(1, (int) (current.time() * (1.0 - getSpeedMultiplier())));
			int needed = Math.min(byRpm, byUpgrade);
			if (crafter.currentNeededTicks != needed) {
				crafter.currentNeededTicks = needed;
			}
		}

		wearRotor(rpm, current != null);
		if (world.getTime() % 20 == 0) {
			syncWithAll();
		}
	}

	/**
	 * Applies rotor wear. Only ticks where the machine is actually processing
	 * count, and higher speeds wear proportionally faster.
	 *
	 * @param rpm     {@code double} the effective rotation speed
	 * @param working {@code boolean} whether a recipe is in progress
	 */
	private void wearRotor(double rpm, boolean working) {
		if (!working || rpm <= 0) {
			return;
		}
		ItemStack rotorStack = inventory.getStack(ROTOR_SLOT);
		if (TRContent.Parts.rotorFromStack(rotorStack) == null) {
			return;
		}
		int interval = Math.max(1, (int) Math.round(TechRebornConfig.isotopeSeparatorRotorWearInterval / Math.max(rpm, 0.01)));
		if (getWorld() == null || getWorld().getTime() % interval != 0) {
			return;
		}
		int damage = rotorStack.getDamage() + 1;
		if (damage >= rotorStack.getMaxDamage()) {
			inventory.setStack(ROTOR_SLOT, ItemStack.EMPTY);
		} else {
			rotorStack.setDamage(damage);
			inventory.setStack(ROTOR_SLOT, rotorStack);
		}
	}

	// =======================================================================
	// output substitution
	// =======================================================================

	/**
	 * @param recipe {@link RebornRecipe} the running recipe
	 * @return {@link List} the outputs that should actually be produced, with
	 *         {@code outputs[0]} swapped for the degraded grade when running
	 *         past the sweet spot
	 */
	List<ItemStack> effectiveOutputs(RebornRecipe recipe) {
		List<ItemStack> outputs = recipe.outputs();
		if (!isDegraded() || !(recipe instanceof IsotopeSeparatorRecipe separator)) {
			return outputs;
		}
		if (separator.degradedOutput().isEmpty() || outputs.isEmpty()) {
			return outputs;
		}
		List<ItemStack> replaced = new java.util.ArrayList<>(outputs);
		replaced.set(0, separator.degradedOutput().get());
		return replaced;
	}

	/**
	 * The crafter used by this machine.
	 * <p>
	 * {@code RecipeCrafter} has no hook for substituting an output stack: its
	 * tick reads {@code currentRecipe.outputs()} directly and the multi-stack
	 * {@code fitStack} overload is private. Since this machine runs one recipe
	 * at a time ({@code maxParallel} stays at 1), overriding the tick and using
	 * the public single-stack {@code fitStack} is enough to swap in the degraded
	 * product. The rest of the logic mirrors the base implementation.
	 */
	private static final class IsotopeSeparatorCrafter extends RecipeCrafter {
		private final IsotopeSeparatorBlockEntity machine;

		IsotopeSeparatorCrafter(net.minecraft.recipe.RecipeType<? extends RebornRecipe> type,
				IsotopeSeparatorBlockEntity blockEntity, int inputs, int outputs,
				RebornInventory<?> inventory, int[] inputSlots, int[] outputSlots,
				IsotopeSeparatorBlockEntity machine) {
			super(type, blockEntity, inputs, outputs, inventory, inputSlots, outputSlots);
			this.machine = machine;
		}

		@Override
		public void updateEntity() {
			World world = blockEntity.getWorld();
			if (world == null || world.isClient) {
				return;
			}
			if (currentRecipe == null && isInvDirty()) {
				updateCurrentRecipe();
			}
			if (currentRecipe == null) {
				setInvDirty(false);
				return;
			}

			if (isInvDirty() && !hasAllInputs()) {
				currentRecipe = null;
				currentTickTime = 0;
				setIsActive();
				setInvDirty(false);
				return;
			}

			if (currentTickTime >= currentNeededTicks && hasAllInputs()) {
				// the only difference from the base class: which stacks come out
				final List<ItemStack> outputs = machine.effectiveOutputs(currentRecipe);
				boolean canGiveInvAll = true;
				for (int i = 0; i < outputs.size() && i < outputSlots.length; i++) {
					if (!canFitOutput(outputs.get(i), outputSlots[i])) {
						canGiveInvAll = false;
					}
				}
				if (canGiveInvAll && currentRecipe.onCraft(blockEntity, currentParallelCount)) {
					for (int i = 0; i < outputs.size() && i < outputSlots.length; i++) {
						// maxParallel is 1 for this machine, so the public
						// single-stack overload is sufficient
						fitStack(outputs.get(i).copy(), outputSlots[i]);
					}
					useAllInputs();
					currentRecipe = null;
					currentTickTime = 0;
					updateCurrentRecipe();
					if (currentRecipe == null) {
						setIsActive();
					}
				}
			} else if (currentTickTime < currentNeededTicks) {
				long useRequirement = getEuPerTick(currentRecipe.power() * currentParallelCount);
				if (energy.tryUseExact(useRequirement)) {
					currentTickTime++;
				}
			}
			setInvDirty(false);
		}
	}

	// =======================================================================
	// GUI
	// =======================================================================

	public int getDisplaySignal() {
		return displaySignal;
	}

	public double getDisplayRpm() {
		return displayRpm;
	}

	public double getDisplayRpmLimit() {
		return displayRpmLimit;
	}

	public boolean isDisplayDegraded() {
		return displayDegraded;
	}

	public void setDisplaySignal(int value) {
		displaySignal = value;
	}

	@Override
	public BuiltScreenHandler createScreenHandler(int syncID, final PlayerEntity player) {
		return new ScreenHandlerBuilder("isotopeseparator").player(player.getInventory()).inventory().hotbar().addInventory()
				.blockEntity(this)
				.slot(INPUT_SLOT, 35, 35).slot(ROTOR_SLOT, 55, 35)
				.outputSlot(PRODUCT_SLOT, 107, 26).outputSlot(TAILS_SLOT, 125, 26)
				.energySlot(ENERGY_SLOT, 8, 72).syncEnergyValue().syncCrafterValue()
				.addInventory().create(this, syncID);
	}

	@Override
	public boolean canCraft(RebornRecipe rebornRecipe) {
		return isMultiblockValid();
	}
}
