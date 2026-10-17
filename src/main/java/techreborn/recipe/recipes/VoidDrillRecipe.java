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
import techreborn.blockentity.machine.multiblock.VoidDrillBlockEntity;

import java.util.List;
import java.util.function.Function;

/**
 * A Void Drill recipe: one biome in, one fluid out.
 * <p>
 * There are no item ingredients and no item outputs - the drill only needs to
 * know what the biome it stands in yields, how much of it, how much power a tick
 * costs and how long a run takes. The run itself is resolved by
 * {@link VoidDrillBlockEntity#completeDrill}, which also debits the chunk's
 * reserve.
 *
 * @param type     {@link RecipeType} the recipe type
 * @param biome    {@link Identifier} the biome this recipe applies to
 * @param fluid    {@link Fluid} the fluid that is produced
 * @param amountMb {@code int} millibuckets produced per run
 * @param power    {@code int} energy per tick
 * @param time     {@code int} ticks per run
 * @param display  {@link ItemStack} shown in recipe viewers as a biome hint; it
 *                 is renamed on the fly and never part of the actual crafting
 */
public record VoidDrillRecipe(RecipeType<VoidDrillRecipe> type, Identifier biome, Fluid fluid, int amountMb,
		int power, int time, ItemStack display) implements RebornRecipe {

	public static final Function<RecipeType<VoidDrillRecipe>, MapCodec<VoidDrillRecipe>> CODEC = type -> RecordCodecBuilder.mapCodec(instance -> instance.group(
		Identifier.CODEC.fieldOf("biome").forGetter(VoidDrillRecipe::biome),
		Registries.FLUID.getEntryCodec().fieldOf("fluid").forGetter(VoidDrillRecipe::fluidRegistryEntry),
		Codecs.POSITIVE_INT.fieldOf("amount").forGetter(VoidDrillRecipe::amountMb),
		Codecs.POSITIVE_INT.fieldOf("power").forGetter(RebornRecipe::power),
		Codecs.POSITIVE_INT.fieldOf("time").forGetter(RebornRecipe::time),
		ItemStack.CODEC.fieldOf("display").forGetter(VoidDrillRecipe::display)
	).apply(instance, (biome, fluid, amount, power, time, display) -> new VoidDrillRecipe(type, biome, fluid, amount, power, time, display)));

	public static final Function<RecipeType<VoidDrillRecipe>, PacketCodec<RegistryByteBuf, VoidDrillRecipe>> PACKET_CODEC = type -> PacketCodec.tuple(
		Identifier.PACKET_CODEC, VoidDrillRecipe::biome,
		PacketCodecs.registryEntry(RegistryKeys.FLUID), VoidDrillRecipe::fluidRegistryEntry,
		PacketCodecs.VAR_INT, VoidDrillRecipe::amountMb,
		PacketCodecs.VAR_INT, RebornRecipe::power,
		PacketCodecs.VAR_INT, RebornRecipe::time,
		ItemStack.PACKET_CODEC, VoidDrillRecipe::display,
		(biome, fluid, amount, power, time, display) -> new VoidDrillRecipe(type, biome, fluid, amount, power, time, display)
	);

	public VoidDrillRecipe(RecipeType<VoidDrillRecipe> type, Identifier biome, RegistryEntry<Fluid> fluid, int amountMb,
			int power, int time, ItemStack display) {
		this(type, biome, fluid.value(), amountMb, power, time, display);
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
		return blockEntity instanceof VoidDrillBlockEntity drill && drill.canRun(this);
	}

	@Override
	public boolean onCraft(BlockEntity blockEntity, int parallel) {
		return blockEntity instanceof VoidDrillBlockEntity drill && drill.completeDrill(this);
	}

	/**
	 * @return {@link ItemStack} the display stack renamed to
	 *         {@code 出产群系：<localized biome> (<registry id>)}
	 */
	public ItemStack displayStack() {
		ItemStack stack = display.copy();
		stack.set(DataComponentTypes.CUSTOM_NAME, Text.translatable("item.techreborn.void_drill.display",
				Text.translatable(biomeTranslationKey()), Text.literal(biome.toString())));
		return stack;
	}

	/**
	 * @return {@link String} the vanilla biome translation key for
	 *         {@link #biome()}
	 */
	public String biomeTranslationKey() {
		return "biome." + biome.getNamespace() + "." + biome.getPath();
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
