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

package techreborn.recipe.recipes;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.recipe.RecipeType;
import net.minecraft.util.dynamic.Codecs;

import reborncore.common.crafting.RebornRecipe;
import reborncore.common.crafting.SizedIngredient;

import techreborn.init.TRContent;

/**
 * Recipe of the Isotope Separator.
 * <p>
 * Identical to {@link CentrifugeRecipe} apart from the optional
 * {@code degraded_output}: when the machine runs faster than the rotor's sweet
 * spot the separation is imperfect and {@code outputs[0]} is replaced by this
 * lower-grade result (the tails in {@code outputs[1]} are unaffected). Recipes
 * without a {@code degraded_output} simply cannot be over-sped into a worse
 * product.
 *
 * @param degradedOutput {@link Optional} replacement for {@code outputs[0]}
 *                       when running degraded, empty if the recipe has no
 *                       lower grade
 */
public record IsotopeSeparatorRecipe(RecipeType<?> type, List<SizedIngredient> ingredients, List<ItemStack> outputs,
		int power, int time, Optional<ItemStack> degradedOutput) implements RebornRecipe {

	public static Function<RecipeType<IsotopeSeparatorRecipe>, MapCodec<IsotopeSeparatorRecipe>> CODEC = type -> RecordCodecBuilder.mapCodec(instance -> instance.group(
		Codec.list(SizedIngredient.CODEC.codec()).fieldOf("ingredients").forGetter(RebornRecipe::ingredients),
		Codec.list(ItemStack.CODEC).fieldOf("outputs").forGetter(RebornRecipe::outputs),
		Codecs.POSITIVE_INT.fieldOf("power").forGetter(RebornRecipe::power),
		Codecs.POSITIVE_INT.fieldOf("time").forGetter(RebornRecipe::time),
		ItemStack.CODEC.optionalFieldOf("degraded_output").forGetter(IsotopeSeparatorRecipe::degradedOutput)
	).apply(instance, (ingredients, outputs, power, time, degradedOutput) ->
		new IsotopeSeparatorRecipe(type, ingredients, outputs, power, time, degradedOutput)));

	public static Function<RecipeType<IsotopeSeparatorRecipe>, PacketCodec<RegistryByteBuf, IsotopeSeparatorRecipe>> PACKET_CODEC = type -> PacketCodec.tuple(
		SizedIngredient.PACKET_CODEC.collect(PacketCodecs.toList()), RebornRecipe::ingredients,
		ItemStack.PACKET_CODEC.collect(PacketCodecs.toList()), RebornRecipe::outputs,
		PacketCodecs.INTEGER, RebornRecipe::power,
		PacketCodecs.INTEGER, RebornRecipe::time,
		ItemStack.PACKET_CODEC.collect(PacketCodecs.toList()), recipe -> recipe.degradedOutput().map(List::of).orElse(List.of()),
		(ingredients, outputs, power, time, degraded) -> new IsotopeSeparatorRecipe(type, ingredients, outputs, power, time,
			degraded.isEmpty() ? Optional.empty() : Optional.of(degraded.get(0)))
	);

	@Override
	public ItemStack createIcon() {
		return new ItemStack(TRContent.Machine.ISOTOPE_SEPARATOR);
	}
}
