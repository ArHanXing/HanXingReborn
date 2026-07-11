package techreborn.client.compat.emi.core;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;

import net.minecraft.util.Identifier;

public class FluidFromContainerEmiRecipe implements EmiRecipe {
	private final Identifier id;
	private final EmiStack output;
	private final EmiIngredient input;
	private final EmiStack container;

	public FluidFromContainerEmiRecipe(Identifier id, EmiStack output, EmiStack input, EmiStack container) {
		this.id = id;
		this.output = output;
		this.input = input;
		this.container = container;
	}

	@Override
	public EmiRecipeCategory getCategory() {
		return new EmiRecipeCategory(
			Identifier.of("emi", "fluid_from_container"),
			EmiStack.EMPTY);
	}

	@Override
	public @Nullable Identifier getId() {
		return id;
	}

	@Override
	public List<EmiIngredient> getInputs() {
		return List.of(input);
	}

	@Override
	public List<EmiStack> getOutputs() {
		return List.of(output, container);
	}

	@Override
	public int getDisplayWidth() {
		return 18 + 24 + 18;
	}

	@Override
	public int getDisplayHeight() {
		return 56;
	}

	@Override
	public void addWidgets(WidgetHolder widgets) {
		widgets.addSlot(input, 0, 0);
		widgets.addSlot(output, 18 + 24, 0).recipeContext(this);
		widgets.addSlot(container, 18 + 24 + 26, 0).recipeContext(this);
	}
}
