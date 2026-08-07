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

package techreborn.client.compat.emi;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import techreborn.multiblock.MultiblockDefinition;
import techreborn.multiblock.MultiblockKeyDefinition;

/**
 * Shows what a JSON-defined multiblock machine is built from.
 * <p>
 * Every non-air key of the structure definition becomes a recipe input
 * (block × count, multi-choice keys like coil lists are shown as "any of"),
 * and the controller block is the output, so players can look up how many of
 * each block a machine needs before assembling it.
 */
public class MultiblockInfoEmiRecipe implements EmiRecipe {

	/** How many input slots are placed on a single row. */
	private static final int COLUMNS = 8;

	private final Identifier id;
	private final EmiStack machine;
	private final List<EmiIngredient> inputs;
	private final int totalBlocks;
	private final int height;

	public MultiblockInfoEmiRecipe(String multiblockId, MultiblockDefinition definition, EmiStack machine) {
		this.id = Identifier.of("techreborn", "multiblock_info/" + multiblockId);
		this.machine = machine;

		// Count every pattern character per key, merging keys that accept the
		// same set of blocks (e.g. two keys pointing at the same casing).
		Map<List<Block>, Integer> counts = new LinkedHashMap<>();
		int total = 0;
		for (List<String> rows : definition.getLayers()) {
			for (String row : rows) {
				for (int x = 0; x < row.length(); x++) {
					char c = row.charAt(x);
					if (c == ' ') {
						continue;
					}
					MultiblockKeyDefinition key = definition.getKeys().get(c);
					if (key == null) {
						continue;
					}
					List<Block> candidates = key.getCandidateBlocks();
					if (candidates.isEmpty()) {
						// air / any / not keys are not a material requirement
						continue;
					}
					List<Block> sorted = candidates.stream()
							.sorted(Comparator.comparing(block -> Registries.BLOCK.getId(block).toString()))
							.toList();
					counts.merge(sorted, 1, Integer::sum);
					total++;
				}
			}
		}
		this.totalBlocks = total;

		List<EmiIngredient> inputs = new ArrayList<>();
		for (Map.Entry<List<Block>, Integer> entry : counts.entrySet()) {
			List<Block> blocks = entry.getKey();
			int amount = entry.getValue();
			if (blocks.size() == 1) {
				inputs.add(toEmiStack(blocks.get(0), amount));
			} else {
				// "Any of these blocks" ingredient
				List<EmiIngredient> variants = blocks.stream()
						.map(block -> toEmiStack(block, 1))
						.map(ingredient -> (EmiIngredient) ingredient)
						.toList();
				inputs.add(EmiIngredient.of(variants, amount));
			}
		}
		this.inputs = inputs;

		int rows = Math.max(1, (int) Math.ceil(inputs.size() / (double) COLUMNS));
		this.height = Math.max(56, 10 + rows * 18 + 6);
	}

	/**
	 * Converts a structure block to an EMI stack, showing fluid blocks (e.g.
	 * the Industrial Grinder's water) as fluid instead of an empty item.
	 */
	private static EmiStack toEmiStack(Block block, int amount) {
		BlockState state = block.getDefaultState();
		if (!state.getFluidState().isEmpty()) {
			return EmiStack.of(state.getFluidState().getFluid(), amount * 1000L);
		}
		return EmiStack.of(new ItemStack(block.asItem(), amount));
	}

	@Override
	public EmiRecipeCategory getCategory() {
		return TREmiPlugin.MULTIBLOCK_INFO_CATEGORY;
	}

	@Override
	public @Nullable Identifier getId() {
		return id;
	}

	@Override
	public List<EmiIngredient> getInputs() {
		return inputs;
	}

	@Override
	public List<EmiStack> getOutputs() {
		return List.of(machine);
	}

	@Override
	public int getDisplayWidth() {
		return 16 + COLUMNS * 18 + 24 + 18;
	}

	@Override
	public int getDisplayHeight() {
		return height;
	}

	@Override
	public void addWidgets(WidgetHolder widgets) {
		widgets.addText(Text.translatable("gui.techreborn.emi.multiblock_total", totalBlocks).asOrderedText(),
			16, 0, 0xFF3F3F3F, false);

		int startX = 16;
		int startY = 10;
		for (int i = 0; i < inputs.size(); i++) {
			int col = i % COLUMNS;
			int row = i / COLUMNS;
			widgets.addSlot(inputs.get(i), startX + col * 18, startY + row * 18);
		}

		int arrowX = startX + COLUMNS * 18 + 4;
		widgets.addTexture(TRTextures.ARROW_RIGHT_EMPTY, arrowX, (height - 10) / 2);

		widgets.addSlot(machine, arrowX + 18 + 2, (height - 26) / 2).large(true).recipeContext(this);
	}
}
