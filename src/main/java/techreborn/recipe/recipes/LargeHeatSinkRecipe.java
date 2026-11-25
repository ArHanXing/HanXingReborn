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
import reborncore.common.crafting.RebornRecipe;
import reborncore.common.crafting.SizedIngredient;
import reborncore.common.fluid.FluidValue;
import reborncore.common.fluid.container.FluidInstance;
import reborncore.common.util.Tank;
import techreborn.blockentity.machine.multiblock.LargeHeatSinkBlockEntity;

import java.util.List;
import java.util.function.Function;

/**
 * A recipe for the Large Heat Sink.
 * <p>
 * Shaped like the industrial grinder's recipe (item inputs, item outputs and an
 * optional fluid product) but the coolant is consumed from the machine's input
 * tank rather than named per recipe: which liquid a recipe needs is its own
 * business, and a recipe may legitimately accept any of them.
 *
 * @param type        {@link RecipeType} the recipe type
 * @param ingredients {@link List} item inputs
 * @param outputs     {@link List} item outputs
 * @param power       {@code int} energy per tick
 * @param time        {@code int} process time in ticks
 * @param fluid       {@link FluidInstance} coolant consumed per run, empty for a
 *                    recipe that only needs the cold
 * @param fluidOutput {@link FluidInstance} fluid produced per run, empty when the
 *                    recipe produces none
 */
public record LargeHeatSinkRecipe(RecipeType<LargeHeatSinkRecipe> type, List<SizedIngredient> ingredients,
		List<ItemStack> outputs, int power, int time, FluidInstance fluid,
		FluidInstance fluidOutput) implements RebornFluidRecipe {

	public static final Function<RecipeType<LargeHeatSinkRecipe>, MapCodec<LargeHeatSinkRecipe>> CODEC = type -> RecordCodecBuilder.mapCodec(instance -> instance.group(
		Codec.list(SizedIngredient.CODEC.codec()).fieldOf("ingredients").forGetter(RebornRecipe::ingredients),
		Codec.list(ItemStack.CODEC).fieldOf("outputs").forGetter(RebornRecipe::outputs),
		Codecs.POSITIVE_INT.fieldOf("power").forGetter(RebornRecipe::power),
		Codecs.POSITIVE_INT.fieldOf("time").forGetter(RebornRecipe::time),
		FluidInstance.CODEC.optionalFieldOf("fluid", FluidInstance.EMPTY).forGetter(RebornFluidRecipe::fluid),
		FluidInstance.CODEC.optionalFieldOf("fluid_output", FluidInstance.EMPTY).forGetter(LargeHeatSinkRecipe::fluidOutput)
	).apply(instance, (ingredients, outputs, power, time, fluid, fluidOutput) ->
		new LargeHeatSinkRecipe(type, ingredients, outputs, power, time, fluid, fluidOutput)));

	public static final Function<RecipeType<LargeHeatSinkRecipe>, PacketCodec<RegistryByteBuf, LargeHeatSinkRecipe>> PACKET_CODEC = type -> PacketCodec.tuple(
		SizedIngredient.PACKET_CODEC.collect(PacketCodecs.toList()), RebornRecipe::ingredients,
		ItemStack.PACKET_CODEC.collect(PacketCodecs.toList()), RebornRecipe::outputs,
		PacketCodecs.INTEGER, RebornRecipe::power,
		PacketCodecs.INTEGER, RebornRecipe::time,
		FluidInstance.PACKET_CODEC, RebornFluidRecipe::fluid,
		FluidInstance.PACKET_CODEC, LargeHeatSinkRecipe::fluidOutput,
		(ingredients, outputs, power, time, fluid, fluidOutput) ->
			new LargeHeatSinkRecipe(type, ingredients, outputs, power, time, fluid, fluidOutput)
	);

	@Override
	public Tank getTank(BlockEntity be) {
		return ((LargeHeatSinkBlockEntity) be).tank;
	}

	@Override
	public boolean canCraft(BlockEntity be) {
		if (!(be instanceof LargeHeatSinkBlockEntity machine)) {
			return false;
		}
		if (!machine.isMultiblockValid()) {
			return false;
		}
		return RebornFluidRecipe.super.canCraft(be) && machine.hasRoomForProduct(this, 1);
	}

	/**
	 * Limits parallel runs by the coolant available and the product tank's free
	 * space, so a saturated machine idles instead of voiding fluid.
	 *
	 * @param be {@link BlockEntity} the machine
	 * @return {@code int} how many runs the tanks can support
	 */
	@Override
	public int getParallelOutputLimit(BlockEntity be) {
		if (!(be instanceof LargeHeatSinkBlockEntity machine)) {
			return 0;
		}
		return machine.parallelLimitFor(this);
	}

	/**
	 * Consumes coolant and deposits the product for {@code parallel} runs.
	 * <p>
	 * Both tanks are re-checked before either is touched: the crafter can keep a
	 * stale parallel count when inputs vanish, and a partial write here would
	 * either void coolant or overfill the product tank.
	 *
	 * @param be       {@link BlockEntity} the machine
	 * @param parallel {@code int} runs finishing together
	 * @return {@code boolean} whether the tanks were changed
	 */
	@Override
	public boolean onCraft(BlockEntity be, int parallel) {
		if (!(be instanceof LargeHeatSinkBlockEntity machine)) {
			return false;
		}
		int runs = Math.max(parallel, 1);
		if (!machine.canConsumeCoolant(this, runs) || !machine.hasRoomForProduct(this, runs)) {
			return false;
		}
		machine.consumeCoolant(this, runs);
		machine.depositProduct(this, runs);
		return true;
	}

	@Override
	public boolean onCraft(BlockEntity be) {
		return onCraft(be, 1);
	}

	/**
	 * @return {@link FluidValue} coolant this recipe consumes per run
	 */
	public FluidValue coolantPerRun() {
		return fluid.getAmount();
	}
}
