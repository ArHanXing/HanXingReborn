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

package techreborn.blockentity.generator.multiblock;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.math.BlockPos;
import reborncore.common.crafting.RecipeUtils;
import reborncore.common.fluid.FluidValue;
import reborncore.common.screen.BuiltScreenHandler;
import reborncore.common.screen.BuiltScreenHandlerProvider;
import reborncore.common.screen.builder.ScreenHandlerBuilder;
import techreborn.config.TechRebornConfig;
import techreborn.init.ModRecipes;
import techreborn.init.TRBlockEntities;
import techreborn.init.TRContent;
import techreborn.recipe.recipes.FluidGeneratorRecipe;

import java.util.ArrayList;
import java.util.List;

/**
 * Universal Chemical Fuel Engine: burns every semi-fluid, gas and diesel
 * generator recipe with 16 parallel fuel streams. Oxygen cells boost the
 * output by 1.5x. All power values come from {@code generators.json}.
 */
public class UniversalChemicalFuelEngineBlockEntity extends LargeFluidGeneratorBlockEntity implements BuiltScreenHandlerProvider {

	public UniversalChemicalFuelEngineBlockEntity(BlockPos pos, BlockState state) {
		super(TRBlockEntities.UNIVERSAL_CHEMICAL_FUEL_ENGINE, pos, state, ModRecipes.SEMI_FLUID_GENERATOR,
				"UniversalChemicalFuelEngineBlockEntity", TechRebornConfig.universalChemicalFuelEngineEnergyPerTick);
	}

	@Override
	protected int getConfiguredMaxOutput() {
		return TechRebornConfig.universalChemicalFuelEngineMaxOutput;
	}

	@Override
	protected int getConfiguredMaxEnergy() {
		return TechRebornConfig.universalChemicalFuelEngineMaxEnergy;
	}

	/**
	 * Accepts every semi-fluid, gas and diesel generator recipe.
	 */
	@Override
	public List<FluidGeneratorRecipe> getRecipes() {
		List<FluidGeneratorRecipe> recipes = new ArrayList<>(RecipeUtils.getRecipes(world, ModRecipes.SEMI_FLUID_GENERATOR));
		recipes.addAll(RecipeUtils.getRecipes(world, ModRecipes.GAS_GENERATOR));
		recipes.addAll(RecipeUtils.getRecipes(world, ModRecipes.DIESEL_GENERATOR));
		return recipes;
	}

	@Override
	public String getMultiblockId() {
		return "universal_chemical_fuel_engine";
	}

	@Override
	public ItemStack getToolDrop(PlayerEntity entityPlayer) {
		return TRContent.Machine.UNIVERSAL_CHEMICAL_FUEL_ENGINE.getStack();
	}

	@Override
	public BuiltScreenHandler createScreenHandler(int syncID, final PlayerEntity player) {
		return new ScreenHandlerBuilder("universalchemicalfuelengine").player(player.getInventory()).inventory().hotbar()
				.addInventory().blockEntity(this).slot(0, 25, 35).outputSlot(1, 25, 55).syncEnergyValue()
				.sync(PacketCodecs.INTEGER, this::getTicksSinceLastChange, this::setTicksSinceLastChange)
				.sync(FluidValue.PACKET_CODEC, this::getTankAmount, this::setTankAmount)
				.sync(tank)
				.addInventory().create(this, syncID);
	}
}
