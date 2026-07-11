package techreborn.client.compat.emi;

import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import techreborn.init.ModRecipes;
import techreborn.init.TRContent;

import java.util.HashMap;
import java.util.Map;

public class TRIntegration {
	public static final Map<EmiRecipeCategory, EmiStack> WORKSTATIONS = new HashMap<>();

	public static EmiStack stackOf(TRContent.Machine machine) {
		return EmiStack.of(machine.block);
	}
}
