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

package techreborn.compat.jade;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import reborncore.api.recipe.IRecipeCrafterProvider;
import reborncore.common.crafting.RebornRecipe;
import reborncore.common.crafting.SizedIngredient;
import reborncore.common.recipes.RecipeCrafter;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IElement;
import snownee.jade.api.ui.IElementHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Shows the currently running recipe on a TechReborn machine:
 * <ul>
 *     <li>Input items (icon + name + count)</li>
 *     <li>Output items (icon + name + count)</li>
 *     <li>Energy usage per tick (base power x parallel)</li>
 *     <li>Current parallel count / maximum parallel</li>
 *     <li>Progress bar and elapsed / total ticks</li>
 * </ul>
 * Values are highlighted in yellow. Recipe state is synced from the server
 * every 250ms via {@link IServerDataProvider}.
 */
public enum MachineRecipeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
	INSTANCE;

	// ---- Server side: sync recipe state ----

	@Override
	public void appendServerData(NbtCompound data, BlockAccessor accessor) {
		if (!(accessor.getBlockEntity() instanceof IRecipeCrafterProvider provider)) {
			return;
		}
		RecipeCrafter crafter = provider.getRecipeCrafter();
		if (crafter == null || crafter.currentRecipe == null) {
			return;
		}

		RebornRecipe recipe = crafter.currentRecipe;
		RegistryWrapper.WrapperLookup lookup = accessor.getLevel().getRegistryManager();

		NbtList inputs = new NbtList();
		for (SizedIngredient ingredient : recipe.ingredients()) {
			ItemStack[] stacks = ingredient.ingredient().getMatchingStacks();
			if (stacks.length > 0) {
				ItemStack representative = stacks[0].copy();
				representative.setCount(ingredient.count());
				encodeStack(inputs, representative, lookup);
			}
		}
		data.put("inputs", inputs);

		NbtList outputs = new NbtList();
		for (ItemStack out : recipe.outputs()) {
			encodeStack(outputs, out, lookup);
		}
		data.put("outputs", outputs);

		data.putInt("power", recipe.power());
		data.putInt("parallel", crafter.getCurrentParallelCount());
		data.putInt("maxParallel", crafter.getMaxParallel());
		data.putInt("tickTime", crafter.currentTickTime);
		data.putInt("neededTicks", crafter.currentNeededTicks);
	}

	private static void encodeStack(NbtList list, ItemStack stack, RegistryWrapper.WrapperLookup lookup) {
		ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, stack)
				.resultOrPartial(error -> {
				})
				.ifPresent(list::add);
	}

	// ---- Client side: render ----

	@Override
	public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
		NbtCompound data = accessor.getServerData();
		if (!data.contains("parallel")) {
			return;
		}

		IElementHelper helper = IElementHelper.get();
		RegistryWrapper.WrapperLookup lookup = accessor.getLevel().getRegistryManager();

		// Inputs: item icons + names
		List<IElement> inputElements = new ArrayList<>();
		for (NbtElement element : data.getList("inputs", NbtElement.COMPOUND_TYPE)) {
			decodeStack((NbtCompound) element, lookup).ifPresent(stack -> {
				inputElements.add(helper.item(stack));
				inputElements.add(helper.text(Text.literal(" " + stack.getName().getString() + " x" + stack.getCount())));
			});
		}
		if (!inputElements.isEmpty()) {
			tooltip.add(inputElements);
		}

		// Outputs: item icons + names
		List<IElement> outputElements = new ArrayList<>();
		for (NbtElement element : data.getList("outputs", NbtElement.COMPOUND_TYPE)) {
			decodeStack((NbtCompound) element, lookup).ifPresent(stack -> {
				outputElements.add(helper.item(stack));
				outputElements.add(helper.text(Text.literal(" " + stack.getName().getString() + " x" + stack.getCount())));
			});
		}
		if (!outputElements.isEmpty()) {
			tooltip.add(outputElements);
		}

		int parallel = data.getInt("parallel");
		int power = data.getInt("power");
		tooltip.add(Text.translatable("jade.techreborn.energy",
				Text.literal(String.valueOf(power * parallel)).formatted(Formatting.YELLOW)));

		tooltip.add(Text.translatable("jade.techreborn.parallel",
				Text.literal(String.valueOf(parallel)).formatted(Formatting.YELLOW),
				Text.literal(String.valueOf(data.getInt("maxParallel"))).formatted(Formatting.YELLOW)));

		int tickTime = data.getInt("tickTime");
		int neededTicks = Math.max(1, data.getInt("neededTicks"));
		tooltip.add(helper.progress(Math.min(1f, (float) tickTime / neededTicks)));
		tooltip.add(Text.translatable("jade.techreborn.progress",
				Text.literal(String.valueOf(tickTime)).formatted(Formatting.YELLOW),
				Text.literal(String.valueOf(neededTicks)).formatted(Formatting.YELLOW)));
	}

	private static Optional<ItemStack> decodeStack(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
		return ItemStack.CODEC.parse(NbtOps.INSTANCE, tag).resultOrPartial(error -> {
		});
	}

	@Override
	public Identifier getUid() {
		return TechRebornJadePlugin.MACHINE_RECIPE;
	}
}
