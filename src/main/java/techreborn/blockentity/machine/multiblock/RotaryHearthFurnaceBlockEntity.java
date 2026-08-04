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
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import reborncore.common.blockentity.MachineBaseBlockEntity;
import reborncore.common.screen.BuiltScreenHandler;
import reborncore.common.screen.BuiltScreenHandlerProvider;
import reborncore.common.screen.builder.ScreenHandlerBuilder;
import reborncore.common.util.RebornInventory;
import techreborn.blocks.misc.BlockCoil;
import techreborn.config.TechRebornConfig;
import techreborn.init.TRBlockEntities;
import techreborn.init.TRContent;
import techreborn.multiblock.CoilHeatScanner;
import techreborn.recipe.RhfRecipeCrafter;

/**
 * Rotary Hearth Furnace (RHF): a large multiblock that runs every Industrial
 * Blast Furnace recipe at 0.5x time and 0.5x power.
 * <p>
 * Uses the same coil system as the IBF (single coil type, coil heat), but has
 * a base heat of {@value #BASE_HEAT} on top of the coils. Its special parallel
 * rule (see {@link RhfRecipeCrafter}) starts at 4 parallels and multiplies by
 * 4 for every 1000 heat above the recipe requirement.
 */
public class RotaryHearthFurnaceBlockEntity extends JsonMultiblockMachineBlockEntity implements BuiltScreenHandlerProvider {

	/** Base heat always available on top of the coil heat. */
	public static final int BASE_HEAT = 1000;

	private int cachedHeat;
	private boolean coilsActive = false;

	public RotaryHearthFurnaceBlockEntity(BlockPos pos, BlockState state) {
		super(TRBlockEntities.ROTARY_HEARTH_FURNACE, pos, state, "RotaryHearthFurnace",
				TechRebornConfig.rotaryHearthFurnaceMaxInput,
				TechRebornConfig.rotaryHearthFurnaceMaxEnergy,
				TRContent.Machine.ROTARY_HEARTH_FURNACE.block, 4);
		final int[] inputs = new int[]{0, 1};
		final int[] outputs = new int[]{2, 3};
		this.inventory = new RebornInventory<>(5, "RotaryHearthFurnaceBlockEntity", 64, this);
		this.crafter = new RhfRecipeCrafter(this, this.inventory, inputs, outputs);
	}

	@Override
	public String getMultiblockId() {
		return "rotary_hearth_furnace";
	}

	/**
	 * Computes the RHF heat: {@value #BASE_HEAT} plus the heat of the coils in
	 * the structure, using the generic {@link CoilHeatScanner}. Only one coil
	 * type may be used; mixed coils return zero heat (disabling the machine).
	 */
	public int getHeat() {
		if (!isMultiblockValid()) {
			return 0;
		}

		int coilHeat = CoilHeatScanner.scanCoilHeat(world, pos, getFacing(), getMultiblockId());
		if (coilHeat < 0) {
			// Mixed coils -> reject
			return 0;
		}
		return BASE_HEAT + coilHeat;
	}

	public void setHeat(final int heat) {
		cachedHeat = heat;
	}

	public int getCachedHeat() {
		return cachedHeat;
	}

	// IContainerProvider
	@Override
	public BuiltScreenHandler createScreenHandler(int syncID, final PlayerEntity player) {
		return new ScreenHandlerBuilder("rotaryhearthfurnace").player(player.getInventory()).inventory().hotbar().addInventory()
				.blockEntity(this).slot(0, 50, 27).slot(1, 50, 47).outputSlot(2, 93, 37).outputSlot(3, 113, 37)
				.energySlot(4, 8, 72).syncEnergyValue().syncCrafterValue()
				.sync(PacketCodecs.INTEGER, this::getHeat, this::setHeat).addInventory().create(this, syncID);
	}

	// ---- tick with coil bloom ----

	@Override
	public void tick(World world, BlockPos pos, BlockState state, MachineBaseBlockEntity blockEntity) {
		super.tick(world, pos, state, blockEntity);
		if (world.isClient) return;

		boolean shouldBloom = crafter.currentRecipe != null && crafter.currentTickTime > 0;
		if (shouldBloom != coilsActive) {
			coilsActive = shouldBloom;
			updateCoilBloomState(shouldBloom);
		}
	}

	/**
	 * Sets the ACTIVE blockstate on every {@link BlockCoil} inside the multiblock
	 * so that coil textures switch to the bloom variant while the machine is working.
	 */
	private void updateCoilBloomState(boolean bloom) {
		if (world == null || world.isClient) return;
		for (BlockPos position : CoilHeatScanner.collectPositions(getPos(), getFacing(), getMultiblockId())) {
			BlockState blockState = world.getBlockState(position);
			if (blockState.getBlock() instanceof BlockCoil) {
				world.setBlockState(position, blockState.with(BlockCoil.ACTIVE, bloom), 2);
			}
		}
	}
}
