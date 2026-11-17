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

package techreborn.recipe.recipes;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.recipe.RecipeType;
import net.minecraft.util.dynamic.Codecs;
import reborncore.common.crafting.RebornFluidRecipe;
import reborncore.common.crafting.SizedIngredient;
import reborncore.common.crafting.RebornRecipe;
import reborncore.common.fluid.FluidValue;
import reborncore.common.fluid.container.FluidInstance;
import reborncore.common.util.Tank;
import techreborn.blockentity.machine.multiblock.IndustrialGrinderBlockEntity;

import java.util.List;
import java.util.function.Function;

public record IndustrialGrinderRecipe(RecipeType<IndustrialGrinderRecipe> type, List<SizedIngredient> ingredients, List<ItemStack> outputs, int power, int time, FluidInstance fluid, FluidInstance fluidOutput) implements RebornFluidRecipe {
	/** Old recipes and datagen callers have no fluid product. */
	public IndustrialGrinderRecipe(RecipeType<IndustrialGrinderRecipe> type, List<SizedIngredient> ingredients, List<ItemStack> outputs, int power, int time, FluidInstance fluid) {
		this(type, ingredients, outputs, power, time, fluid, FluidInstance.EMPTY);
	}
	public static final Function<RecipeType<IndustrialGrinderRecipe>, MapCodec<IndustrialGrinderRecipe>> CODEC = type -> RecordCodecBuilder.mapCodec(instance -> instance.group(
		Codec.list(SizedIngredient.CODEC.codec()).fieldOf("ingredients").forGetter(RebornRecipe::ingredients),
		Codec.list(ItemStack.CODEC).fieldOf("outputs").forGetter(RebornRecipe::outputs),
		Codecs.POSITIVE_INT.fieldOf("power").forGetter(RebornRecipe::power),
		Codecs.POSITIVE_INT.fieldOf("time").forGetter(RebornRecipe::time),
		FluidInstance.CODEC.fieldOf("fluid").forGetter(RebornFluidRecipe::fluid),
		FluidInstance.CODEC.optionalFieldOf("fluid_output", FluidInstance.EMPTY).forGetter(IndustrialGrinderRecipe::fluidOutput)
	).apply(instance, (ingredients, outputs, power, time, fluid, fluidOutput) -> new IndustrialGrinderRecipe(type, ingredients, outputs, power, time, fluid, fluidOutput)));
	public static final Function<RecipeType<IndustrialGrinderRecipe>, PacketCodec<RegistryByteBuf, IndustrialGrinderRecipe>> PACKET_CODEC = type -> PacketCodec.tuple(
		SizedIngredient.PACKET_CODEC.collect(PacketCodecs.toList()), RebornRecipe::ingredients,
		ItemStack.PACKET_CODEC.collect(PacketCodecs.toList()), RebornRecipe::outputs,
		PacketCodecs.INTEGER, RebornRecipe::power,
		PacketCodecs.INTEGER, RebornRecipe::time,
		FluidInstance.PACKET_CODEC, RebornFluidRecipe::fluid,
		FluidInstance.PACKET_CODEC, IndustrialGrinderRecipe::fluidOutput,
		(ingredients, outputs, power, time, fluid, fluidOutput) -> new IndustrialGrinderRecipe(type, ingredients, outputs, power, time, fluid, fluidOutput)
	);

	@Override
	public Tank getTank(BlockEntity be) {
		IndustrialGrinderBlockEntity blockEntity = (IndustrialGrinderBlockEntity) be;
		return blockEntity.getTank();
	}

	@Override
	public boolean canCraft(BlockEntity be) {
		IndustrialGrinderBlockEntity blockEntity = (IndustrialGrinderBlockEntity) be;
		return blockEntity.isMultiblockValid() && RebornFluidRecipe.super.canCraft(be)
				&& inputFluidLimit(blockEntity) >= 1 && canOutputFluid(blockEntity, 1);
	}

	@Override
	public int getParallelOutputLimit(BlockEntity be) {
		IndustrialGrinderBlockEntity machine = (IndustrialGrinderBlockEntity) be;
		int limit = inputFluidLimit(machine);
		if (limit <= 0) return 0;
		if (fluidOutput.isEmpty()) return limit;
		Tank output = machine.outputTank;
		if (!output.isEmpty() && !output.getResource().equals(fluidOutput.fluidVariant())) return 0;
		long amount = fluidOutput.getAmount().getRawValue();
		if (amount <= 0) return limit;
		long free = output.getFreeSpace().getRawValue();
		return (int) Math.min(limit, free / amount);
	}

	@Override
	public boolean onCraft(BlockEntity be) {
		return onCraft(be, 1);
	}

	@Override
	public boolean onCraft(BlockEntity be, int parallel) {
		IndustrialGrinderBlockEntity machine = (IndustrialGrinderBlockEntity) be;
		int runs = Math.max(1, parallel);
		// RecipeCrafter can retain its previous parallel count if fresh inputs
		// disappear, so re-check both tanks before mutating either. Structure
		// validity is re-checked by canCraft whenever a recipe is selected; it is
		// deliberately not repeated here, because the machine may already have
		// spent its energy on the run.
		if (inputFluidLimit(machine) < runs || !canOutputFluid(machine, runs)) {
			return false;
		}
		if (!fluid().isEmpty()) {
			FluidValue consumed = fluid().getAmount().multiply(runs);
			machine.tank.modifyFluid(current -> current.subtractAmount(consumed));
		}
		if (!fluidOutput.isEmpty()) {
			FluidValue produced = fluidOutput.getAmount().multiply(runs);
			if (machine.outputTank.isEmpty()) {
				machine.outputTank.setFluidInstance(fluidOutput.withAmount(produced));
			} else {
				machine.outputTank.modifyFluid(current -> current.addAmount(produced));
			}
		}
		return true;
	}

	private int inputFluidLimit(IndustrialGrinderBlockEntity machine) {
		if (fluid().isEmpty()) return Integer.MAX_VALUE;
		if (!machine.tank.getResource().equals(fluid().fluidVariant())) return 0;
		long amount = fluid().getAmount().getRawValue();
		if (amount <= 0) return Integer.MAX_VALUE;
		return (int) Math.min(Integer.MAX_VALUE, machine.tank.getAmount() / amount);
	}

	private boolean canOutputFluid(IndustrialGrinderBlockEntity machine, int runs) {
		if (fluidOutput.isEmpty()) return true;
		Tank output = machine.outputTank;
		if (!output.isEmpty() && !output.getResource().equals(fluidOutput.fluidVariant())) return false;
		long amount = fluidOutput.getAmount().getRawValue();
		if (amount <= 0) return true;
		return amount > 0 && output.getFreeSpace().getRawValue() / amount >= runs;
	}
}
