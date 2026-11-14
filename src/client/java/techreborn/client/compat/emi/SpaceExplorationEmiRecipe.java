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
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.api.widget.Widget;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** One body page: stage tabs and simultaneous tier columns are alternative costs. */
public final class SpaceExplorationEmiRecipe implements EmiRecipe {
	private static final List<String> STAGES = List.of("survey", "land", "outpost");
	private static final int COLUMN_WIDTH = 118;
	private final List<SpaceEmiData.RecipeView> variants;
	private final List<String> stages;
	private final Identifier id;
	private final List<EmiIngredient> inputs;
	private final List<EmiStack> outputs;
	private String selectedStage;

	public SpaceExplorationEmiRecipe(List<SpaceEmiData.RecipeView> variants) {
		if (variants.isEmpty() || variants.stream().anyMatch(view -> view.kind().equals("mining") || !view.bodyId().equals(variants.getFirst().bodyId()))) {
			throw new IllegalArgumentException("Exploration page must contain one body and no mining variants");
		}
		this.variants = List.copyOf(variants);
		stages = STAGES.stream().filter(stage -> variants.stream().anyMatch(view -> view.kind().equals(stage))).toList();
		selectedStage = variants.stream().filter(view -> !view.completed()).map(SpaceEmiData.RecipeView::kind).findFirst().orElse(stages.getFirst());
		Identifier body = variants.getFirst().bodyId();
		id = Identifier.of("techreborn", "/space/exploration/" + java.util.HexFormat.of().formatHex(body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		List<EmiIngredient> allInputs = new ArrayList<>();
		List<EmiStack> allOutputs = new ArrayList<>();
		for (var view : variants) {
			view.inputs().forEach(stack -> allInputs.add(emi(stack)));
			view.returns().forEach(stack -> allOutputs.add(emi(stack)));
		}
		inputs = List.copyOf(allInputs); outputs = List.copyOf(allOutputs);
	}
	private static EmiStack emi(ItemStack stack) { return EmiStack.of(stack.copyWithCount(1), stack.getCount()); }
	@Override public EmiRecipeCategory getCategory() { return TREmiPlugin.SPACE_EXPLORATION_CATEGORY; }
	@Override public Identifier getId() { return id; }
	@Override public List<EmiIngredient> getInputs() { return inputs; }
	@Override public List<EmiStack> getOutputs() { return outputs; }
	@Override public boolean supportsRecipeTree() { return false; }
	@Override public int getDisplayWidth() { return COLUMN_WIDTH * 3; }
	@Override public int getDisplayHeight() { return 150; }
	@Override public void addWidgets(WidgetHolder widgets) {
		var first = variants.getFirst();
		var renderer = MinecraftClient.getInstance().textRenderer;
		widgets.addText(Text.literal(renderer.trimToWidth(Text.translatable(first.nameKey()).getString(), getDisplayWidth() - 25)), 2, 0, 0x304B62, false);
		widgets.addSlot(EmiStack.of(net.minecraft.registry.Registries.BLOCK.get(first.marker())), getDisplayWidth() - 20, 0);
		Text alternatives = Text.translatable("gui.techreborn.space.emi.alternatives");
		widgets.addText(Text.literal(renderer.trimToWidth(alternatives.getString(), getDisplayWidth() - 4)), 2, 13, 0x666666, false);
		widgets.addTooltipText(List.of(alternatives, Text.translatable("gui.techreborn.space.emi.constraints")), 0, 12, getDisplayWidth(), 12);
		for (int index = 0; index < stages.size(); index++) {
			String stage = stages.get(index); int x = index * COLUMN_WIDTH;
			widgets.add(new Widget() {
				private final Bounds bounds = new Bounds(x, 27, COLUMN_WIDTH - 2, 15);
				@Override public Bounds getBounds() { return bounds; }
				@Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
					boolean selected = selectedStage.equals(stage);
					context.fill(x, 27, x + COLUMN_WIDTH - 2, 42, selected ? 0xFF3C6578 : 0xFFAAAAAA);
					context.drawText(renderer, renderer.trimToWidth(Text.translatable("gui.techreborn.space.action." + stage).getString(), COLUMN_WIDTH - 6), x + 3, 30, 0xFFFFFFFF, false);
				}
				@Override public boolean mouseClicked(int mouseX, int mouseY, int button) {
					if (button == 0 && bounds.contains(mouseX, mouseY)) { selectedStage = stage; return true; }
					return false;
				}
			});
		}
		for (int tier = 1; tier <= 3; tier++) {
			int x = (tier - 1) * COLUMN_WIDTH + 2; int selectedTier = tier;
			widgets.addDrawable(x, 45, COLUMN_WIDTH - 4, 101, (context, mouseX, mouseY, delta) -> {
				var view = selected(selectedTier);
				context.drawText(renderer, Text.translatable("gui.techreborn.space.emi.tier", selectedTier), 0, 0, 0x304B62, false);
				if (view == null) {
					context.drawText(renderer, renderer.trimToWidth(Text.translatable("gui.techreborn.space.emi.unavailable").getString(), COLUMN_WIDTH - 4), 0, 14, 0x777777, false);
					return;
				}
				Text powerTime = Text.translatable("gui.techreborn.space.emi.power_time", view.power(), String.format(Locale.ROOT, "%.2f", view.time() / 20D));
				context.drawText(renderer, renderer.trimToWidth(powerTime.getString(), COLUMN_WIDTH - 4), 0, 12, 0x404040, false);
				context.drawText(renderer, Text.translatable("gui.techreborn.space.emi.inputs"), 0, 25, 0x666666, false);
				context.drawText(renderer, Text.translatable("gui.techreborn.space.emi.outputs"), 58, 25, 0x666666, false);
				context.drawText(renderer, renderer.trimToWidth(Text.translatable("gui.techreborn.space.emi." + (view.completed() ? "completed" : "next")).getString(), COLUMN_WIDTH - 4), 0, 93, view.completed() ? 0x777777 : 0x287845, false);
			});
			widgets.addTooltip((mx, my) -> {
				var view = selected(selectedTier);
				if (view == null) { return List.of(); }
				return List.of(net.minecraft.client.gui.tooltip.TooltipComponent.of(Text.translatable("gui.techreborn.space.emi.power_time", view.power(), String.format(Locale.ROOT, "%.2f", view.time() / 20D)).asOrderedText()));
			}, x, 56, COLUMN_WIDTH - 4, 12);
			for (var view : variants) {
				if (view.tier() != tier) { continue; }
				for (int i = 0; i < view.inputs().size(); i++) { widgets.add(new StageSlot(emi(view.inputs().get(i)), x + i % 3 * 18, 83 + i / 3 * 18, view.kind())); }
				for (int i = 0; i < view.returns().size(); i++) { widgets.add(new StageSlot(emi(view.returns().get(i)), x + 58 + i % 3 * 18, 83 + i / 3 * 18, view.kind()).recipeContext(this)); }
			}
		}
	}
	private SpaceEmiData.RecipeView selected(int tier) {
		return variants.stream().filter(candidate -> candidate.kind().equals(selectedStage) && candidate.tier() == tier).findFirst().orElse(null);
	}

	/** Hidden tabs have no hover/click bounds, preserving native EMI slot behavior. */
	private final class StageSlot extends SlotWidget {
		private final String stage;
		StageSlot(EmiIngredient stack, int x, int y, String stage) { super(stack, x, y); this.stage = stage; }
		@Override public Bounds getBounds() { return selectedStage.equals(stage) ? super.getBounds() : Bounds.EMPTY; }
		@Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
			if (selectedStage.equals(stage)) { super.render(context, mouseX, mouseY, delta); }
		}
	}
}
