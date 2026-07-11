package techreborn.client.compat.emi.core;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;

import net.minecraft.util.Identifier;

import techreborn.client.compat.emi.TREmiPlugin;

public class FluidIntoContainerEmiRecipe implements EmiRecipe {
	private final Identifier id;
	private final EmiStack input;
	private final EmiIngredient containerInput;
	private final EmiStack container;

	public FluidIntoContainerEmiRecipe(Identifier id, EmiStack input, EmiStack containerInput, EmiStack container) {
		this.id = id;
		this.input = input;
		this.containerInput = containerInput;
		this.container = container;
	}

	@Override
	public EmiRecipeCategory getCategory() {
		return TREmiPlugin.FLUID_INTO_CONTAINER_CATEGORY;
	}

	@Override
	public @Nullable Identifier getId() {
		return id;
	}

	@Override
	public List<EmiIngredient> getInputs() {
		return List.of(input, containerInput);
	}

	@Override
	public List<EmiStack> getOutputs() {
		return List.of(container);
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
		widgets.addSlot(containerInput, 0 + 18 + 24, 0);
		widgets.addSlot(container, 0 + 18 + 24 + 26, 0).recipeContext(this);
	}
}
