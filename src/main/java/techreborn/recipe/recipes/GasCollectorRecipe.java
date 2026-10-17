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

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.recipe.RecipeType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.dynamic.Codecs;
import reborncore.common.crafting.RebornRecipe;
import reborncore.common.crafting.SizedIngredient;
import techreborn.blockentity.machine.multiblock.GasCollectorBlockEntity;

import java.util.List;
import java.util.function.Function;

/**
 * A Gas Collector recipe: one dimension in, one fluid out.
 * <p>
 * Same idea as the Void Drill, but keyed on the dimension instead of the biome,
 * and with no reserve to deplete - the machine simply keeps producing while it
 * stands in that dimension.
 *
 * @param type      {@link RecipeType} the recipe type
 * @param dimension {@link Identifier} the dimension this recipe applies to
 * @param fluid     {@link Fluid} the fluid that is collected
 * @param amountMb  {@code int} millibuckets produced per run
 * @param power     {@code int} energy per tick
 * @param time      {@code int} ticks per run
 * @param display   {@link ItemStack} shown in recipe viewers as a dimension
 *                  hint; it is renamed on the fly and never part of crafting
 */
public record GasCollectorRecipe(RecipeType<GasCollectorRecipe> type, Identifier dimension, Fluid fluid, int amountMb,
		int power, int time, ItemStack display) implements RebornRecipe {

	public static final Function<RecipeType<GasCollectorRecipe>, MapCodec<GasCollectorRecipe>> CODEC = type -> RecordCodecBuilder.mapCodec(instance -> instance.group(
		Identifier.CODEC.fieldOf("dimension").forGetter(GasCollectorRecipe::dimension),
		Registries.FLUID.getEntryCodec().fieldOf("fluid").forGetter(GasCollectorRecipe::fluidRegistryEntry),
		Codecs.POSITIVE_INT.fieldOf("amount").forGetter(GasCollectorRecipe::amountMb),
		Codecs.POSITIVE_INT.fieldOf("power").forGetter(RebornRecipe::power),
		Codecs.POSITIVE_INT.fieldOf("time").forGetter(RebornRecipe::time),
		ItemStack.CODEC.fieldOf("display").forGetter(GasCollectorRecipe::display)
	).apply(instance, (dimension, fluid, amount, power, time, display) -> new GasCollectorRecipe(type, dimension, fluid, amount, power, time, display)));

	public static final Function<RecipeType<GasCollectorRecipe>, PacketCodec<RegistryByteBuf, GasCollectorRecipe>> PACKET_CODEC = type -> PacketCodec.tuple(
		Identifier.PACKET_CODEC, GasCollectorRecipe::dimension,
		PacketCodecs.registryEntry(RegistryKeys.FLUID), GasCollectorRecipe::fluidRegistryEntry,
		PacketCodecs.VAR_INT, GasCollectorRecipe::amountMb,
		PacketCodecs.VAR_INT, RebornRecipe::power,
		PacketCodecs.VAR_INT, RebornRecipe::time,
		ItemStack.PACKET_CODEC, GasCollectorRecipe::display,
		(dimension, fluid, amount, power, time, display) -> new GasCollectorRecipe(type, dimension, fluid, amount, power, time, display)
	);

	public GasCollectorRecipe(RecipeType<GasCollectorRecipe> type, Identifier dimension, RegistryEntry<Fluid> fluid,
			int amountMb, int power, int time, ItemStack display) {
		this(type, dimension, fluid.value(), amountMb, power, time, display);
	}

	@Override
	public List<SizedIngredient> ingredients() {
		return List.of();
	}

	@Override
	public List<ItemStack> outputs() {
		return List.of();
	}

	@Override
	public boolean canCraft(BlockEntity blockEntity) {
		return blockEntity instanceof GasCollectorBlockEntity collector && collector.canRun(this);
	}

	@Override
	public boolean onCraft(BlockEntity blockEntity, int parallel) {
		return blockEntity instanceof GasCollectorBlockEntity collector && collector.completeCollect(this);
	}

	/**
	 * @return {@link ItemStack} the display stack renamed to
	 *         {@code 采集维度：<localized dimension> (<registry id>)}
	 */
	public ItemStack displayStack() {
		ItemStack stack = display.copy();
		stack.set(DataComponentTypes.CUSTOM_NAME, Text.translatable("item.techreborn.gas_collector.display",
				Text.translatable(dimensionTranslationKey()), Text.literal(dimension.toString())));
		return stack;
	}

	/**
	 * @return {@link String} the vanilla dimension translation key
	 */
	public String dimensionTranslationKey() {
		return "dimension." + dimension.getNamespace() + "." + dimension.getPath();
	}

	@Override
	public ItemStack createIcon() {
		return displayStack();
	}

	public Fluid getFluid() {
		return fluid;
	}

	public RegistryEntry<Fluid> fluidRegistryEntry() {
		return fluid.getRegistryEntry();
	}
}
