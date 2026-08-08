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
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import reborncore.common.blockentity.MachineBaseBlockEntity;
import reborncore.common.multiblock.IMultiblockPart;
import reborncore.common.screen.BuiltScreenHandler;
import reborncore.common.screen.BuiltScreenHandlerProvider;
import reborncore.common.screen.builder.ScreenHandlerBuilder;
import reborncore.common.util.RebornInventory;
import techreborn.blockentity.machine.multiblock.casing.MachineCasingBlockEntity;
import techreborn.blocks.misc.BlockCoil;
import techreborn.config.TechRebornConfig;
import techreborn.init.TRBlockEntities;
import techreborn.init.TRContent;
import techreborn.multiblocks.MultiBlockCasing;
import techreborn.multiblock.CoilHeatScanner;
import techreborn.recipe.BlastFurnaceRecipeCrafter;

import java.util.Optional;

public class IndustrialBlastFurnaceBlockEntity extends JsonMultiblockMachineBlockEntity implements BuiltScreenHandlerProvider {

	private int cachedHeat;
	private boolean coilsActive = false;

	public IndustrialBlastFurnaceBlockEntity(BlockPos pos, BlockState state) {
		super(TRBlockEntities.INDUSTRIAL_BLAST_FURNACE, pos, state, "IndustrialBlastFurnace", TechRebornConfig.industrialBlastFurnaceMaxInput, TechRebornConfig.industrialBlastFurnaceMaxEnergy, TRContent.Machine.INDUSTRIAL_BLAST_FURNACE.block, 4);
		final int[] inputs = new int[]{0, 1};
		final int[] outputs = new int[]{2, 3};
		this.inventory = new RebornInventory<>(5, "IndustrialBlastFurnaceBlockEntity", 64, this);
		this.crafter = new BlastFurnaceRecipeCrafter(this, this.inventory, inputs, outputs);
		this.crafter.setMaxParallel(1);
	}

	@Override
	public String getMultiblockId() {
		return "industrial_blast_furnace";
	}

	/**
	 * Computes the Industrial Blast Furnace's heat by scanning the JSON-defined
	 * multiblock structure for coil blocks. Only one coil type may be used;
	 * mixed coils return zero heat (disabling the machine).
	 * <p>
	 * This replaces the old hard-coded "bottom center casing" scan with the
	 * generic {@link CoilHeatScanner} shared with other coil machines (e.g.
	 * the Rotary Hearth Furnace). Each coil tier provides a fixed, GT5-style
	 * temperature.
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
		return coilHeat;
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
		return new ScreenHandlerBuilder("blastfurnace").player(player.getInventory()).inventory().hotbar().addInventory()
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
		getMultiblockCasing().ifPresent(casing -> {
			for (final IMultiblockPart part : casing.connectedParts) {
				BlockPos partPos = part.getPos();
				BlockState partState = world.getBlockState(partPos);
				if (partState.getBlock() instanceof BlockCoil) {
					world.setBlockState(partPos, partState.with(BlockCoil.ACTIVE, bloom), 2);
				}
			}
		});
	}

	/**
	 * Resolves the assembled multiblock casing from the controller position.
	 */
	private Optional<MultiBlockCasing> getMultiblockCasing() {
		if (world == null || !isMultiblockValid()) return Optional.empty();
		final BlockPos location = pos.offset(getFacing().getOpposite(), 2);
		final BlockEntity blockEntity = world.getBlockEntity(location);
		if (blockEntity instanceof MachineCasingBlockEntity casing
				&& casing.isConnected()
				&& casing.getMultiblockController().isAssembled()) {
			return Optional.of(casing.getMultiblockController());
		}
		return Optional.empty();
	}
}
