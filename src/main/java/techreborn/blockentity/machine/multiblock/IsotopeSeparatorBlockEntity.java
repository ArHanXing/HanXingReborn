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

/**
 * The Isotope Separator: a tier 2 multiblock that separates a feed into a
 * product and a tails stream.
 * <p>
 * <b>Speed.</b> The rotation speed {@code r} comes from the redstone signal
 * applied to the controller itself (0..15 scaled to 0..1) and is capped by the
 * rotor in slot 1. A missing rotor still runs, at a 25% floor.
 * <p>
 * <b>The trade-off.</b> Faster rotation shortens the process but costs more
 * energy: the process time scales with {@code 1 - 0.5r} while the draw scales
 * with {@code 1 + 3r}. Speed is therefore always paid for in power, so
 * time-accelerating upgrades can make the machine burn more energy but never
 * remove the cost of running fast.
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

	/** Rotation cost factor currently in effect, folded into the power draw. */
	private double rotorEnergyFactor = 1.0;

	public IsotopeSeparatorBlockEntity(BlockPos pos, BlockState state) {
		super(TRBlockEntities.ISOTOPE_SEPARATOR, pos, state, "IsotopeSeparator",
				TechRebornConfig.isotopeSeparatorMaxInput,
				TechRebornConfig.isotopeSeparatorMaxEnergy,
				TRContent.Machine.ISOTOPE_SEPARATOR.block, ENERGY_SLOT);
		final int[] inputs = new int[]{INPUT_SLOT, ROTOR_SLOT};
		final int[] outputs = new int[]{PRODUCT_SLOT, TAILS_SLOT};
		this.inventory = new RebornInventory<>(ENERGY_SLOT + 1, "IsotopeSeparatorBlockEntity", 64, this);
		this.crafter = new RecipeCrafter(ModRecipes.ISOTOPE_SEPARATOR, this, 1, 2,
				this.inventory, inputs, outputs);
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
	 * @param rpm {@code double} the effective rotation speed
	 * @return {@code int} the process time for the given speed
	 */
	public int effectiveTime(int recipeTime, double rpm) {
		return (int) Math.max(1, Math.round(recipeTime * timeMultiplier(rpm)));
	}

	/**
	 * @param rpm {@code double} the effective rotation speed
	 * @return {@code double} the process-time multiplier at that speed
	 *         ({@code 1.0 - timeReduction * rpm})
	 */
	public double timeMultiplier(double rpm) {
		return Math.max(0.01, 1.0 - TechRebornConfig.isotopeSeparatorTimeReduction * rpm);
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

	/**
	 * @param rpm {@code double} the effective rotation speed
	 * @return {@code int} ticks of processing per point of rotor wear; wear
	 *         scales inversely with the speed
	 */
	public int wearIntervalTicks(double rpm) {
		return Math.max(1, (int) Math.round(TechRebornConfig.isotopeSeparatorRotorWearInterval / Math.max(rpm, 0.01)));
	}

	/**
	 * @return {@link ItemStack} the rotor slot contents, may be empty
	 */
	public ItemStack getRotorStack() {
		return inventory.getStack(ROTOR_SLOT);
	}

	/**
	 * @return {@code boolean} {@code true} if a rotor is installed
	 */
	public boolean isRotorInstalled() {
		return TRContent.Parts.rotorFromStack(inventory.getStack(ROTOR_SLOT)) != null;
	}

	/**
	 * Estimated remaining rotor life, using the current speed's wear interval.
	 *
	 * @return {@code long} ticks the installed rotor would still last, or
	 *         {@code -1} when no rotor is installed
	 */
	public long getRotorRemainingTicks() {
		ItemStack rotor = getRotorStack();
		if (TRContent.Parts.rotorFromStack(rotor) == null) {
			return -1;
		}
		int remainingWear = Math.max(0, rotor.getMaxDamage() - rotor.getDamage());
		return (long) remainingWear * wearIntervalTicks(getRpm());
	}

	// =======================================================================
	// energy
	// =======================================================================

	/**
	 * The rotor's rotation cost, folded into the machine's power multiplier.
	 */
	@Override
	public double getPowerMultiplier() {
		return super.getPowerMultiplier() * rotorEnergyFactor;
	}

	/**
	 * The crafter prices a tick through {@code IUpgradeHandler.getEuPerTick},
	 * whose base implementation multiplies by the raw {@code powerMultiplier}
	 * field and therefore ignores {@link #getPowerMultiplier()}. Overriding it
	 * here is what makes the rotation cost (and the Shine rotor's discount)
	 * actually reach the energy draw; overriding the getter alone would leave
	 * the machine paying base power at every speed.
	 */
	@Override
	public long getEuPerTick(long baseEu) {
		return (long) (baseEu * getPowerMultiplier());
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
		int interval = wearIntervalTicks(rpm);
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

	/**
	 * Formats a tick count as a compact duration ({@code 45s}, {@code 2m 30s},
	 * {@code 1h 5m}). Used by the GUI and Jade for the rotor life estimate.
	 *
	 * @param ticks {@code long} duration in ticks
	 * @return {@link String} the formatted duration
	 */
	public static String formatTicks(long ticks) {
		long seconds = Math.max(0, ticks) / 20;
		long minutes = seconds / 60;
		long hours = minutes / 60;
		if (hours > 0) {
			return hours + "h " + (minutes % 60) + "m";
		}
		if (minutes > 0) {
			return minutes + "m " + (seconds % 60) + "s";
		}
		return seconds + "s";
	}

	// =======================================================================
	// GUI
	// =======================================================================

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
