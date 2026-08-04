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
import reborncore.common.screen.BuiltScreenHandler;
import reborncore.common.screen.BuiltScreenHandlerProvider;
import reborncore.common.screen.builder.ScreenHandlerBuilder;
import reborncore.common.util.RebornInventory;
import reborncore.common.util.Tank;
import techreborn.config.TechRebornConfig;
import techreborn.init.ModRecipes;
import techreborn.init.TRBlockEntities;
import techreborn.init.TRContent;
import techreborn.recipe.ProxyRecipeCrafter;

import java.util.List;

public class IndustrialGrinderBlockEntity extends JsonMultiblockMachineBlockEntity implements BuiltScreenHandlerProvider {

	public static final FluidValue TANK_CAPACITY = FluidValue.BUCKET.multiply(16);
	public final Tank tank;
	int ticksSinceLastChange;

	public IndustrialGrinderBlockEntity(BlockPos pos, BlockState state) {
		super(TRBlockEntities.INDUSTRIAL_GRINDER, pos, state, "IndustrialGrinder", TechRebornConfig.industrialGrinderMaxInput, TechRebornConfig.industrialGrinderMaxEnergy, TRContent.Machine.INDUSTRIAL_GRINDER.block, 7);
		final int[] inputs = new int[]{0, 1};
		final int[] outputs = new int[]{2, 3, 4, 5};
		this.inventory = new RebornInventory<>(8, "IndustrialGrinderBlockEntity", 64, this);
		// Runs the industrial grinder's own recipes (priority) plus every regular
		// grinder recipe (0.5x time, 0.8x power, no fluid required)
		this.crafter = new ProxyRecipeCrafter(
				ModRecipes.INDUSTRIAL_GRINDER,
				List.of(ModRecipes.GRINDER),
				0.5F, 0.8F,
				this, 1, 4, this.inventory, inputs, outputs);
		this.crafter.setMaxParallel(16);
		this.tank = new Tank("IndustrialGrinderBlockEntity", IndustrialGrinderBlockEntity.TANK_CAPACITY);
		this.ticksSinceLastChange = 0;
	}

	@Override
	public String getMultiblockId() {
		return "industrial_grinder";
	}

	@Override
	public boolean canCraft(RebornRecipe rebornRecipe) {
		// Ensures proxy (grinder) recipes also require a valid multiblock structure
		return isMultiblockValid();
	}

	// TilePowerAcceptor
	@Override
	public void tick(World world, BlockPos pos, BlockState state, MachineBaseBlockEntity blockEntity) {
		super.tick(world, pos, state, blockEntity);
		if (world == null || world.isClient){
			return;
		}

		ticksSinceLastChange++;
		// Check cells input slot 2 time per second
		if (ticksSinceLastChange >= 10) {
			if (!inventory.getStack(1).isEmpty()) {
				FluidUtils.drainContainers(tank, inventory, 1, 6);
				FluidUtils.fillContainers(tank, inventory, 1, 6);
			}
			ticksSinceLastChange = 0;
		}
	}

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

	// TileMachineBase
	@Nullable
	@Override
	public Tank getTank() {
		return tank;
	}

	// IContainerProvider
	@Override
	public BuiltScreenHandler createScreenHandler(int syncID, final PlayerEntity player) {
		// fluidSlot first to support automation and shift-click
		return new ScreenHandlerBuilder("industrialgrinder").player(player.getInventory()).inventory().hotbar().addInventory()
				.blockEntity(this).fluidSlot(1, 34, 35).slot(0, 84, 43).outputSlot(2, 126, 18).outputSlot(3, 126, 36)
				.outputSlot(4, 126, 54).outputSlot(5, 126, 72).outputSlot(6, 34, 55).energySlot(7, 8, 72)
				.sync(tank).syncEnergyValue().syncCrafterValue().addInventory().create(this, syncID);
	}

}
