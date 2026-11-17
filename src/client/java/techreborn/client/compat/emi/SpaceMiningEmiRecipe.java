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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** One mining page per body; tier equipment and ore choices are alternatives, not combined costs. */
public final class SpaceMiningEmiRecipe implements EmiRecipe {
	private static final int COLUMN_WIDTH = 118;
	private static final int ORE_COLUMNS = 18;
	private static final int ORES_PER_PAGE = ORE_COLUMNS * 2;
	private final Identifier id;
	private final Identifier bodyId;
	private final String nameKey;
	private final Identifier marker;
	private final boolean outpost;
	private final boolean landingNeeded;
	private final List<TierView> tiers;
	private final List<OreView> ores;
	private final List<EmiIngredient> inputs;
	private final List<EmiStack> outputs;
	private int orePage;

	public SpaceMiningEmiRecipe(List<SpaceEmiData.RecipeView> views) {
		if (views.isEmpty() || views.size() > 256 * 3 || views.stream().anyMatch(view -> !view.kind().equals("mining")
				|| !view.bodyId().equals(views.getFirst().bodyId()))) {
			throw new IllegalArgumentException("Mining page must contain one body's bounded mining views");
		}
		SpaceEmiData.RecipeView first = views.getFirst();
		bodyId = first.bodyId(); nameKey = first.nameKey(); marker = first.marker(); outpost = first.outpost(); landingNeeded = first.landingNeeded();
		id = Identifier.of("techreborn", "/space/mining/body/" + java.util.HexFormat.of().formatHex(bodyId.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));

		Map<Integer, TierView> byTier = new java.util.TreeMap<>();
		Map<String, OreView> byResource = new LinkedHashMap<>();
		for (SpaceEmiData.RecipeView view : views) {
			if (!view.nameKey().equals(nameKey) || !view.marker().equals(marker) || view.outpost() != outpost || view.landingNeeded() != landingNeeded) {
				throw new IllegalArgumentException("Inconsistent body mining metadata");
			}
			TierView candidate = new TierView(view.tier(), view.power(), view.time(), view.inputs(), view.returns());
			TierView priorTier = byTier.putIfAbsent(view.tier(), candidate);
			if (priorTier != null && !priorTier.sameScheme(candidate)) { throw new IllegalArgumentException("Mining scheme differs between resources"); }
			if (view.resourceId().isBlank()) { throw new IllegalArgumentException("Mining resource id is missing"); }
			OreView ore = new OreView(view.resourceId(), view.resource(), view.yield());
			OreView priorOre = byResource.putIfAbsent(view.resourceId(), ore);
			if (priorOre != null && !priorOre.sameOre(ore)) { throw new IllegalArgumentException("Mining resource differs between tiers"); }
		}
		tiers = List.copyOf(byTier.values()); ores = List.copyOf(byResource.values());
		if (tiers.isEmpty() || ores.isEmpty() || ores.size() > 256) { throw new IllegalArgumentException("Empty or oversized mining body page"); }

		List<EmiIngredient> allInputs = new ArrayList<>();
		List<EmiStack> allOutputs = new ArrayList<>();
		for (TierView tier : tiers) {
			tier.inputs().forEach(stack -> allInputs.add(emi(stack)));
			tier.returns().forEach(stack -> allOutputs.add(emi(stack)));
		}
		// Each ore is an alternative selection shown once, never repeated per tier.
		for (OreView ore : ores) { if (ore.yield() > 0) { allOutputs.add(EmiStack.of(ore.stack(), ore.yield())); } }
		inputs = List.copyOf(allInputs); outputs = List.copyOf(allOutputs);
	}

	private static EmiStack emi(ItemStack stack) { return EmiStack.of(stack.copyWithCount(1), stack.getCount()); }
	@Override public EmiRecipeCategory getCategory() { return TREmiPlugin.SPACE_ELEVATOR_MINER_CATEGORY; }
	@Override public Identifier getId() { return id; }
	@Override public List<EmiIngredient> getInputs() { return inputs; }
	@Override public List<EmiStack> getOutputs() { return outputs; }
	@Override public boolean supportsRecipeTree() { return false; }
	@Override public int getDisplayWidth() { return COLUMN_WIDTH * 3; }
	@Override public int getDisplayHeight() { return 162; }

	@Override public void addWidgets(WidgetHolder widgets) {
		var renderer = MinecraftClient.getInstance().textRenderer;
		widgets.addText(Text.literal(renderer.trimToWidth(Text.translatable(nameKey).getString(), getDisplayWidth() - 25)), 2, 0, 0x304B62, false);
		widgets.addSlot(EmiStack.of(net.minecraft.registry.Registries.BLOCK.get(marker)), getDisplayWidth() - 20, 0);
		widgets.addTooltipText(List.of(Text.translatable(nameKey), Text.translatable("gui.techreborn.space.emi.constraints")), getDisplayWidth() - 20, 0, 18, 18);
		String alternatives = Text.translatable("gui.techreborn.space.emi.alternatives").getString();
		widgets.addText(Text.literal(renderer.trimToWidth(alternatives, landingNeeded ? 176 : getDisplayWidth() - 4)), 2, 13, 0x666666, false);
		if (landingNeeded) {
			String landing = Text.translatable("gui.techreborn.space.emi.landing_needed").getString();
			widgets.addText(Text.literal(renderer.trimToWidth(landing, 164)), 184, 13, 0xAA6633, false);
		}

		for (int index = 0; index < 3; index++) {
			int x = index * COLUMN_WIDTH + 2;
			if (index < tiers.size()) { addTier(widgets, tiers.get(index), x); }
			else { widgets.addText(Text.translatable("gui.techreborn.space.emi.unavailable"), x, 22, 0x777777, false); }
		}

		int pageCount = pageCount();
		int start = orePage * ORES_PER_PAGE;
		int end = Math.min(ores.size(), start + ORES_PER_PAGE);
		String options = Text.translatable("gui.techreborn.space.emi.ore_options").getString();
		widgets.addText(Text.literal(renderer.trimToWidth(options, 132)), 2, 96, 0x304B62, false);
		widgets.addText(Text.literal(renderer.trimToWidth(Text.translatable("gui.techreborn.space.emi.ore_page", start + 1, end, ores.size()).getString(), 150)), 138, 96, 0x666666, false);
		widgets.add(new PageButton(306, 92, -1, pageCount, renderer));
		widgets.add(new PageButton(330, 92, 1, pageCount, renderer));
		for (int index = start; index < end; index++) {
			OreView ore = ores.get(index);
			int local = index - start;
			int x = 2 + local % ORE_COLUMNS * 18;
			int y = 114 + local / ORE_COLUMNS * 18;
			widgets.add(new OreSlot(ore, x, y));
		}
	}

	private void addTier(WidgetHolder widgets, TierView tier, int x) {
		var renderer = MinecraftClient.getInstance().textRenderer;
		widgets.addText(Text.translatable("gui.techreborn.space.emi.tier", tier.tier()), x, 22, 0x304B62, false);
		Text powerTime = Text.translatable("gui.techreborn.space.emi.power_time", tier.power(), String.format(Locale.ROOT, "%.2f", tier.time() / 20D));
		widgets.addText(Text.literal(renderer.trimToWidth(powerTime.getString(), COLUMN_WIDTH - 4)), x, 34, 0x404040, false);
		widgets.addText(Text.translatable("gui.techreborn.space.emi.inputs"), x, 45, 0x666666, false);
		widgets.addText(Text.translatable("gui.techreborn.space.emi.outputs"), x + 49, 45, 0x666666, false);
		for (int index = 0; index < tier.inputs().size(); index++) {
			int slotX = x + 2 + index % 2 * 18;
			int slotY = 56 + index / 2 * 18;
			widgets.addSlot(emi(tier.inputs().get(index)), slotX, slotY).appendTooltip(Text.translatable("gui.techreborn.space.emi.equipment_once"));
		}
		for (int index = 0; index < tier.returns().size(); index++) {
			int slotX = x + 49 + index % 3 * 18;
			int slotY = 56 + index / 3 * 18;
			widgets.add(new EquipmentReturnSlot(tier.returns().get(index), slotX, slotY));
		}
	}

	private int pageCount() { return (ores.size() + ORES_PER_PAGE - 1) / ORES_PER_PAGE; }

	private final class PageButton extends Widget {
		private final Bounds bounds;
		private final int direction;
		private final int pageCount;
		private final net.minecraft.client.font.TextRenderer renderer;
		private PageButton(int x, int y, int direction, int pageCount, net.minecraft.client.font.TextRenderer renderer) {
			this.bounds = new Bounds(x, y, 20, 16); this.direction = direction; this.pageCount = pageCount; this.renderer = renderer;
		}
		@Override public Bounds getBounds() { return pageCount > 1 ? bounds : Bounds.EMPTY; }
		@Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
			if (pageCount <= 1) { return; }
			boolean enabled = direction < 0 ? orePage > 0 : orePage + 1 < pageCount;
			context.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), enabled ? 0xFF3C6578 : 0xFF777777);
			String label = direction < 0 ? "<" : ">";
			context.drawText(renderer, label, bounds.x() + 7, bounds.y() + 4, 0xFFFFFFFF, false);
		}
		@Override public boolean mouseClicked(int mouseX, int mouseY, int button) {
			if (button != 0 || !bounds.contains(mouseX, mouseY)) { return false; }
			int next = orePage + direction;
			if (next >= 0 && next < pageCount) { orePage = next; }
			return true;
		}
	}

	private final class EquipmentReturnSlot extends SlotWidget {
		private final ItemStack stack;
		private EquipmentReturnSlot(ItemStack stack, int x, int y) {
			super(emi(stack), x, y);
			this.stack = stack.copy();
			recipeContext(SpaceMiningEmiRecipe.this);
			appendTooltip(Text.translatable("gui.techreborn.space.emi.equipment_once"));
		}
		@Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
			super.render(context, mouseX, mouseY, delta);
			drawCount(context, stack.getCount(), x, y);
		}
	}

	private final class OreSlot extends SlotWidget {
		private final OreView ore;
		private OreSlot(OreView ore, int x, int y) {
			super(emi(ore.stack()), x, y);
			this.ore = ore;
			appendTooltip(Text.translatable("gui.techreborn.space.emi.ore_yield", ore.yield()));
			appendTooltip(Text.translatable("gui.techreborn.space.emi.ore_alternatives"));
			if (ore.yield() == 0) { appendTooltip(Text.translatable("gui.techreborn.space.emi.ore_zero")); }
		}
		@Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
			super.render(context, mouseX, mouseY, delta);
			drawCount(context, ore.yield(), x, y);
		}
	}

	private static void drawCount(DrawContext context, int count, int x, int y) {
		var renderer = MinecraftClient.getInstance().textRenderer;
		String text = Integer.toString(count);
		context.fill(x + 7, y + 10, x + 18, y + 18, 0xC0000000);
		context.drawText(renderer, renderer.trimToWidth(text, 11), x + 7, y + 10, 0xFFFFFFFF, false);
	}

	private record TierView(int tier, long power, long time, List<ItemStack> inputs, List<ItemStack> returns) {
		private TierView { inputs = copies(inputs); returns = copies(returns); }
		private boolean sameScheme(TierView other) {
			return power == other.power && time == other.time && stackListsEqual(inputs, other.inputs) && stackListsEqual(returns, other.returns);
		}
	}
	private record OreView(String id, ItemStack stack, int yield) {
		private OreView { stack = stack.copy(); }
		@Override public ItemStack stack() { return stack.copy(); }
		private boolean sameOre(OreView other) { return yield == other.yield && ItemStack.areEqual(stack, other.stack); }
	}
	private static List<ItemStack> copies(List<ItemStack> stacks) { return stacks.stream().map(ItemStack::copy).toList(); }
	private static boolean stackListsEqual(List<ItemStack> left, List<ItemStack> right) {
		if (left.size() != right.size()) { return false; }
		for (int index = 0; index < left.size(); index++) { if (!ItemStack.areEqual(left.get(index), right.get(index))) { return false; } }
		return true;
	}
}
