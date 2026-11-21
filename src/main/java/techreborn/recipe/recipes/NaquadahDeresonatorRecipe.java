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
import net.minecraft.fluid.Fluid;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.recipe.RecipeType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.dynamic.Codecs;
import reborncore.common.crafting.RebornRecipe;
import reborncore.common.crafting.SizedIngredient;
import techreborn.blockentity.machine.multiblock.NaquadahDeresonatorBlockEntity;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * A Naquadah Deresonator recipe.
 * <p>
 * The fluid is recorded as an id only, with no amount: the machine drains
 * whatever the tank holds during the run and converts the total into a success
 * chance (see {@link NaquadahDeresonatorBlockEntity#successRate}). That is why
 * this recipe implements {@link RebornRecipe} rather than the fluid recipe
 * interface - no fixed amount is ever taken up front.
 *
 * @param type        {@link RecipeType} the recipe type
 * @param ingredients {@link List} of item inputs (two are consumed per run)
 * @param outputs     {@link List} of possible outputs (up to four)
 * @param power       {@code int} energy per tick
 * @param time        {@code int} base process time in ticks, before the
 *                    dimension penalty and speed upgrades
 * @param fluid       {@link Fluid} the fluid that feeds the success chance
 * @param fluidOutput {@link Optional} the fluid the run produces, keyed by id
 *                    only: the amount always mirrors what the run drew from the
 *                    input tank, so a recipe never spells out a quantity
 */
public record NaquadahDeresonatorRecipe(RecipeType<NaquadahDeresonatorRecipe> type, List<SizedIngredient> ingredients,
		List<ItemStack> outputs, int power, int time, Fluid fluid,
		Optional<RegistryEntry<Fluid>> fluidOutput) implements RebornRecipe {

	public static final Function<RecipeType<NaquadahDeresonatorRecipe>, MapCodec<NaquadahDeresonatorRecipe>> CODEC = type -> RecordCodecBuilder.mapCodec(instance -> instance.group(
		Codec.list(SizedIngredient.CODEC.codec()).fieldOf("ingredients").forGetter(RebornRecipe::ingredients),
		Codec.list(ItemStack.CODEC).fieldOf("outputs").forGetter(RebornRecipe::outputs),
		Codecs.POSITIVE_INT.fieldOf("power").forGetter(RebornRecipe::power),
		Codecs.POSITIVE_INT.fieldOf("time").forGetter(RebornRecipe::time),
		Registries.FLUID.getEntryCodec().fieldOf("fluid").forGetter(NaquadahDeresonatorRecipe::fluidRegistryEntry),
		Registries.FLUID.getEntryCodec().optionalFieldOf("fluid_output").forGetter(NaquadahDeresonatorRecipe::fluidOutput)
	).apply(instance, (ingredients, outputs, power, time, fluid, fluidOutput) -> new NaquadahDeresonatorRecipe(type, ingredients, outputs, power, time, fluid.value(), fluidOutput)));

	public static final Function<RecipeType<NaquadahDeresonatorRecipe>, PacketCodec<RegistryByteBuf, NaquadahDeresonatorRecipe>> PACKET_CODEC = type -> PacketCodec.tuple(
		SizedIngredient.PACKET_CODEC.collect(PacketCodecs.toList()), RebornRecipe::ingredients,
		ItemStack.PACKET_CODEC.collect(PacketCodecs.toList()), RebornRecipe::outputs,
		PacketCodecs.INTEGER, RebornRecipe::power,
		PacketCodecs.INTEGER, RebornRecipe::time,
		PacketCodecs.registryEntry(RegistryKeys.FLUID), NaquadahDeresonatorRecipe::fluidRegistryEntry,
		PacketCodecs.optional(PacketCodecs.registryEntry(RegistryKeys.FLUID)), NaquadahDeresonatorRecipe::fluidOutput,
		(ingredients, outputs, power, time, fluid, fluidOutput) -> new NaquadahDeresonatorRecipe(type, ingredients, outputs, power, time, fluid.value(), fluidOutput)
	);

	public NaquadahDeresonatorRecipe(RecipeType<NaquadahDeresonatorRecipe> type, List<SizedIngredient> ingredients,
			List<ItemStack> outputs, int power, int time, RegistryEntry<Fluid> fluid) {
		this(type, ingredients, outputs, power, time, fluid.value(), Optional.empty());
	}

	public NaquadahDeresonatorRecipe(RecipeType<NaquadahDeresonatorRecipe> type, List<SizedIngredient> ingredients,
			List<ItemStack> outputs, int power, int time, Fluid fluid) {
		this(type, ingredients, outputs, power, time, fluid, Optional.empty());
	}

	@Override
	public boolean canCraft(final BlockEntity blockEntity) {
		// Delegate to the machine instead of re-deriving the conditions here. The
		// override used to check only "structure assembled" and "product has room",
		// which silently bypassed every other gate the machine applies - most
		// importantly the one that refuses to start on an empty fluid tank.
		return blockEntity instanceof NaquadahDeresonatorBlockEntity machine && machine.canCraft(this);
	}

	/**
	 * Deposits the run's fluid product.
	 * <p>
	 * The recipe names a fluid id but no amount: the product mirrors what the run
	 * actually drew from the input tank, which the machine tallies as
	 * {@link NaquadahDeresonatorBlockEntity#getRunFluidRaw()}. Nothing is produced
	 * when the roll fails - the fluid bought the success chance, it is not
	 * recycled.
	 *
	 * @param blockEntity {@link BlockEntity} the machine
	 * @param parallel    {@code int} unused, the deresonator always runs one at a
	 *                    time
	 * @return {@code boolean} whether the product could be stored
	 */
	@Override
	public boolean onCraft(BlockEntity blockEntity, int parallel) {
		if (blockEntity instanceof NaquadahDeresonatorBlockEntity machine) {
			return machine.depositRunFluidProduct(outputFluid().orElse(null));
		}
		return true;
	}

	public Fluid getFluid() {
		return fluid;
	}

	public RegistryEntry<Fluid> fluidRegistryEntry() {
		return fluid.getRegistryEntry();
	}

	/**
	 * @return {@link Optional} the fluid the run produces, empty when this recipe
	 *         yields no fluid
	 */
	public Optional<Fluid> outputFluid() {
		return fluidOutput.map(RegistryEntry::value);
	}
}
