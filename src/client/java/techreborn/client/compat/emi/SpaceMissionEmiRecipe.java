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

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** One authoritative stage/variant or a single selectable mining resource view. */
public final class SpaceMissionEmiRecipe implements EmiRecipe {
	private final SpaceEmiData.RecipeView view;
	private final List<EmiIngredient> inputs;
	private final List<EmiStack> outputs;
	public SpaceMissionEmiRecipe(SpaceEmiData.RecipeView view) {
		this.view = view;
		inputs = view.inputs().stream().map(stack -> (EmiIngredient) emi(stack)).toList();
		List<EmiStack> outputs = new ArrayList<>(view.returns().stream().map(SpaceMissionEmiRecipe::emi).toList());
		if (view.kind().equals("mining") && view.yield() > 0) { outputs.add(EmiStack.of(view.resource(), view.yield())); }
		this.outputs = List.copyOf(outputs);
	}
	private SpaceMissionEmiRecipe() { view = null; inputs = List.of(); outputs = List.of(); }
	public static SpaceMissionEmiRecipe miningInfo() { return new SpaceMissionEmiRecipe(); }

	private static Text clipped(Text text) { return Text.literal(MinecraftClient.getInstance().textRenderer.trimToWidth(text.getString(), 210)); }
	private static EmiStack emi(ItemStack stack) { return EmiStack.of(stack.copyWithCount(1), stack.getCount()); }
	@Override public EmiRecipeCategory getCategory() { return view == null || view.kind().equals("mining") ? TREmiPlugin.SPACE_ELEVATOR_MINER_CATEGORY : TREmiPlugin.SPACE_EXPLORATION_CATEGORY; }
	@Override public Identifier getId() { return view == null ? Identifier.of("techreborn", "/space/mining/info") : view.id(); }
	@Override public List<EmiIngredient> getInputs() { return inputs; }
	@Override public List<EmiStack> getOutputs() { return outputs; }
	@Override public boolean supportsRecipeTree() { return false; } // Stage/team gates and multi-resource selection are not craft-tree recipes.
	@Override public int getDisplayWidth() { return 216; }
	@Override public int getDisplayHeight() { return view == null ? 90 : view.kind().equals("mining") ? view.landingNeeded() ? 161 : 147 : 129; }
	@Override public void addWidgets(WidgetHolder widgets) {
		if (view == null) {
			widgets.addText(clipped(Text.translatable("gui.techreborn.space.emi.mining_info_title")), 2, 0, 0x304B62, false);
			widgets.addSlot(TREmiPlugin.SPACE_ELEVATOR_MINER_STACK, 2, 17);
			widgets.addText(clipped(Text.translatable("gui.techreborn.space.emi.mining_info")), 2, 40, 0x666666, false);
			widgets.addText(clipped(Text.translatable("gui.techreborn.space.emi.mining_info_empty")), 2, 55, 0x666666, false);
			widgets.addTooltipText(List.of(Text.translatable("gui.techreborn.space.emi.mining_info"),
					Text.translatable("gui.techreborn.space.emi.mining_info_empty")), 0, 35, 216, 50);
			return;
		}
		String body = Text.translatable(view.nameKey()).getString();
		widgets.addText(Text.literal(MinecraftClient.getInstance().textRenderer.trimToWidth(body, 184)), 2, 0, 0x304B62, false);
		// Context icon only: not a consumed ingredient or a produced resource.
		var marker = net.minecraft.registry.Registries.BLOCK.get(view.marker()).asItem().getDefaultStack();
		widgets.addSlot(EmiStack.of(marker), 196, 0);
		widgets.addTooltipText(List.of(Text.translatable(view.nameKey()), Text.translatable("gui.techreborn.space.emi.constraints")), 196, 0, 18, 18);
		Text stage = Text.translatable("gui.techreborn.space.emi.stage", Text.translatable("gui.techreborn.space.action." + view.kind()), view.tier());
		widgets.addText(stage, 2, 13, 0x404040, false);
		widgets.addText(Text.translatable("gui.techreborn.space.emi.power_time", view.power(), String.format(Locale.ROOT, "%.2f", view.time() / 20D)), 2, 26, 0x404040, false);
		widgets.addText(Text.translatable("gui.techreborn.space.emi.inputs"), 2, 41, 0x404040, false);
		widgets.addText(Text.translatable("gui.techreborn.space.emi.outputs"), 116, 41, 0x404040, false);
		for (int i = 0; i < inputs.size(); i++) { widgets.addSlot(inputs.get(i), 2 + i % 3 * 18, 53 + i / 3 * 18); }
		for (int i = 0; i < outputs.size(); i++) { widgets.addSlot(outputs.get(i), 116 + i % 5 * 18, 53 + i / 5 * 18).recipeContext(this); }
		List<Text> tooltip = new ArrayList<>();
		tooltip.add(Text.translatable("gui.techreborn.space.emi.constraints"));
		tooltip.add(Text.translatable("gui.techreborn.space.emi.input_components"));
		if (view.kind().equals("mining")) {
			widgets.addText(clipped(Text.translatable("gui.techreborn.space.emi." + (view.outpost() ? "outpost" : "long_range"))), 2, 110, 0x406B50, false);
			widgets.addText(clipped(Text.translatable("gui.techreborn.space.emi.selection")), 2, 126, 0x666666, false);
			tooltip.add(Text.translatable("gui.techreborn.space.emi.selection"));
			tooltip.add(Text.translatable("gui.techreborn.space.emi.resource"));
			tooltip.add(Text.translatable("gui.techreborn.space.emi." + (view.outpost() ? "outpost" : "long_range")));
			if (view.landingNeeded()) {
				widgets.addText(clipped(Text.translatable("gui.techreborn.space.emi.landing_needed")), 2, 141, 0xAA6633, false);
				tooltip.add(Text.translatable("gui.techreborn.space.emi.landing_needed"));
			}
			if (view.yield() == 0) {
				widgets.addSlot(EmiStack.of(view.resource()), 188, 89);
				tooltip.add(Text.translatable("gui.techreborn.space.emi.zero_yield"));
			}
		} else {
			widgets.addText(Text.translatable("gui.techreborn.space.emi." + (view.completed() ? "completed" : "next")), 2, 111, view.completed() ? 0x777777 : 0x287845, false);
		}
		widgets.addTooltipText(tooltip, 0, 108, getDisplayWidth(), getDisplayHeight() - 108);
	}
}
