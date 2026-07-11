package techreborn.client.compat.emi;

import java.util.List;

import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.widget.WidgetHolder;
import techreborn.recipe.recipes.FusionReactorRecipe;

import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.text.Text;
import net.minecraft.recipe.RecipeEntry;

import techreborn.client.compat.emi.core.UIUtils;

public class FusionReactorEmiRecipe extends TREmiRecipe<FusionReactorRecipe> {
	public FusionReactorEmiRecipe(RecipeEntry<FusionReactorRecipe> recipe) {
		super(recipe);
	}

	@Override
	public EmiRecipeCategory getCategory() {
		return TREmiPlugin.FUSION_REACTOR_CATEGORY;
	}

	@Override
	public int getDisplayWidth() {
		return 16 + 18 + 24 + 26 + 24 + 18;
	}

	@Override
	public int getDisplayHeight() {
		return 50;
	}

	@Override
	public void addWidgets(WidgetHolder widgets) {
		widgets.addSlot(getInput(0), 16, (50 - 18) / 2);
		widgets.addSlot(getOutput(0), 16 + 18 + 24, (50 - 26) / 2).large(true).recipeContext(this);
		widgets.addSlot(getInput(1), 16 + 18 + 24 + 26 + 24, (50 - 18) / 2);

		int power = recipe.power();
		int displayedPower;
		String tooltip;
		if (power < 0) {
			displayedPower = -power;
			tooltip = "recipe_power.consumed";
		} else {
			displayedPower = power;
			tooltip = "recipe_power.produced";
		}

		widgets.addTexture(TRTextures.ENERGY_BAR_EMPTY, 0, 0).tooltip((mx, my) -> List.of(
			TooltipComponent.of(
				Text.translatable("tooltip.techreborn.emi." + tooltip, displayedPower).asOrderedText())));
		widgets.addAnimatedTexture(TRTextures.ENERGY_BAR_FULL, 0, 0, 100000 * 1000 / displayedPower * 50, false, true,
			power < 0);

		TRUIUtils.arrowRight(widgets, recipe, 16 + 18 + 4, (50 - 10) / 2);
		TRUIUtils.arrowLeft(widgets, recipe, 16 + 18 + 24 + 26 + 4, (50 - 10) / 2);
		UIUtils.cookTime(widgets, recipe.time(), 16, 0);
		widgets.addText(Text.translatable("gui.techreborn.emi.start_e",
			UIUtils.metricNumber(recipe.getStartEnergy())).asOrderedText(), 16, 50 - 9, 0xFF3F3F3F, false);
		widgets.addText(Text.translatable("gui.techreborn.emi.min_size", recipe.getMinSize()).asOrderedText(),
			16 + 18 + 24 + 13, 0, 0xFF3F3F3F, false);
	}
}
