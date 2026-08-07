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

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.EmiRecipeSorting;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.stack.Comparison;
import dev.emi.emi.api.stack.EmiStack;
import techreborn.init.ModRecipes;
import techreborn.init.TRContent;
import techreborn.items.DynamicCellItem;

import net.minecraft.registry.Registries;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.util.Identifier;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.Items;

import java.util.List;
import java.util.Map;

import techreborn.client.compat.emi.core.EmiTextures;
import techreborn.client.compat.emi.core.LongHolder;
import techreborn.multiblock.MultiblockDefinition;
import techreborn.multiblock.MultiblockDefinitionLoader;

@EmiEntrypoint
public class TREmiPlugin implements EmiPlugin {
	public static final EmiStack CELL = EmiStack.of(TRContent.CELL);
	public static final EmiStack ALLOY_SMELTER_STACK = EmiStack.of(TRContent.Machine.ALLOY_SMELTER);
	public static final EmiStack IRON_ALLOY_FURNACE_STACK = EmiStack.of(TRContent.Machine.IRON_ALLOY_FURNACE);
	public static final EmiStack ASSEMBLY_MACHINE_STACK = EmiStack.of(TRContent.Machine.ASSEMBLY_MACHINE);
	public static final EmiStack BLAST_FURNACE_STACK = EmiStack.of(TRContent.Machine.INDUSTRIAL_BLAST_FURNACE);
	public static final EmiStack INDUSTRIAL_CENTRIFUGE_STACK = EmiStack.of(TRContent.Machine.INDUSTRIAL_CENTRIFUGE);
	public static final EmiStack CHEMICAL_REACTOR_STACK = EmiStack.of(TRContent.Machine.CHEMICAL_REACTOR);
	public static final EmiStack COMPRESSOR_STACK = EmiStack.of(TRContent.Machine.COMPRESSOR);
	public static final EmiStack DISTILLATION_TOWER_STACK = EmiStack.of(TRContent.Machine.DISTILLATION_TOWER);
	public static final EmiStack EXTRACTOR_STACK = EmiStack.of(TRContent.Machine.EXTRACTOR);
	public static final EmiStack GRINDER_STACK = EmiStack.of(TRContent.Machine.GRINDER);
	public static final EmiStack IMPLOSION_COMPRESSOR_STACK = EmiStack.of(TRContent.Machine.IMPLOSION_COMPRESSOR);
	public static final EmiStack INDUSTRIAL_ELECTROLYZER_STACK = EmiStack.of(TRContent.Machine.INDUSTRIAL_ELECTROLYZER);
	public static final EmiStack INDUSTRIAL_GRINDER_STACK = EmiStack.of(TRContent.Machine.INDUSTRIAL_GRINDER);
	public static final EmiStack INDUSTRIAL_SAWMILL_STACK = EmiStack.of(TRContent.Machine.INDUSTRIAL_SAWMILL);
	public static final EmiStack SCRAP_BOX_STACK = EmiStack.of(TRContent.SCRAP_BOX);
	public static final EmiStack SCRAPBOXINATOR_STACK = EmiStack.of(TRContent.Machine.SCRAPBOXINATOR);
	public static final EmiStack VACUUM_FREEZER_STACK = EmiStack.of(TRContent.Machine.VACUUM_FREEZER);
	public static final EmiStack FLUID_REPLICATOR_STACK = EmiStack.of(TRContent.Machine.FLUID_REPLICATOR);
	public static final EmiStack FUSION_CONTROL_COMPUTER_STACK = EmiStack.of(TRContent.Machine.FUSION_CONTROL_COMPUTER);
	public static final EmiStack ROLLING_MACHINE_STACK = EmiStack.of(TRContent.Machine.ROLLING_MACHINE);
	public static final EmiStack SOLID_CANNING_MACHINE_STACK = EmiStack.of(TRContent.Machine.SOLID_CANNING_MACHINE);
	public static final EmiStack WIRE_MILL_STACK = EmiStack.of(TRContent.Machine.WIRE_MILL);

	public static final EmiStack THERMAL_GENERATOR_STACK = EmiStack.of(TRContent.Machine.THERMAL_GENERATOR);
	public static final EmiStack GAS_TURBINE_STACK = EmiStack.of(TRContent.Machine.GAS_TURBINE);
	public static final EmiStack DIESEL_GENERATOR_STACK = EmiStack.of(TRContent.Machine.DIESEL_GENERATOR);
	public static final EmiStack SEMI_FLUID_GENERATOR_STACK = EmiStack.of(TRContent.Machine.SEMI_FLUID_GENERATOR);
	public static final EmiStack PLASMA_GENERATOR_STACK = EmiStack.of(TRContent.Machine.PLASMA_GENERATOR);

	public static final EmiStack AUTO_CRAFTING_TABLE_STACK = EmiStack.of(TRContent.Machine.AUTO_CRAFTING_TABLE);
	public static final EmiStack IRON_FURNACE_STACK = EmiStack.of(TRContent.Machine.IRON_FURNACE);
	public static final EmiStack ELECTRIC_FURNACE_STACK = EmiStack.of(TRContent.Machine.ELECTRIC_FURNACE);
	public static final EmiStack LARGE_CHEMICAL_REACTOR_STACK = EmiStack.of(TRContent.Machine.LARGE_CHEMICAL_REACTOR);

	public static final EmiRecipeCategory ALLOY_SMELTER_CATEGORY =
		new EmiRecipeCategory(trId("alloy_smelter"), ALLOY_SMELTER_STACK, EmiTextures.ALLOY_SMELTING,
			EmiRecipeSorting.compareOutputThenInput());
	public static final EmiRecipeCategory ASSEMBLING_MACHINE_CATEGORY =
		new EmiRecipeCategory(trId("assembling_machine"), ASSEMBLY_MACHINE_STACK, EmiTextures.ASSEMBLING,
			EmiRecipeSorting.compareOutputThenInput());
	public static final EmiRecipeCategory BLAST_FURNACE_CATEGORY =
		new EmiRecipeCategory(trId("blast_furnace"), BLAST_FURNACE_STACK, EmiTextures.BLAST_FURNACE,
			EmiRecipeSorting.compareOutputThenInput());
	public static final EmiRecipeCategory CENTRIFUGE_CATEGORY =
		new EmiRecipeCategory(trId("centrifuge"), INDUSTRIAL_CENTRIFUGE_STACK, EmiTextures.CENTRIFUGE,
			EmiRecipeSorting.compareOutputThenInput());
	public static final EmiRecipeCategory CHEMICAL_REACTOR_CATEGORY =
		new EmiRecipeCategory(trId("chemical_reactor"), CHEMICAL_REACTOR_STACK, EmiTextures.CHEMICAL_REACTING,
			EmiRecipeSorting.compareOutputThenInput());
	public static final EmiRecipeCategory COMPRESSOR_CATEGORY =
		new EmiRecipeCategory(trId("compressor"), COMPRESSOR_STACK, EmiTextures.COMPRESSING,
			EmiRecipeSorting.compareOutputThenInput());
	public static final EmiRecipeCategory DISTILLATION_TOWER_CATEGORY =
		new EmiRecipeCategory(trId("distillation_tower"), DISTILLATION_TOWER_STACK, EmiTextures.DISTILLATION_TOWER,
			EmiRecipeSorting.compareOutputThenInput());
	public static final EmiRecipeCategory EXTRACTOR_CATEGORY =
		new EmiRecipeCategory(trId("extractor"), EXTRACTOR_STACK, EmiTextures.EXTRACTING,
			EmiRecipeSorting.compareOutputThenInput());
	public static final EmiRecipeCategory GRINDER_CATEGORY =
		new EmiRecipeCategory(trId("grinder"), GRINDER_STACK, EmiTextures.GRINDING,
			EmiRecipeSorting.compareOutputThenInput());
	public static final EmiRecipeCategory IMPLOSION_COMPRESSOR_CATEGORY =
		new EmiRecipeCategory(trId("implosion_compressor"), IMPLOSION_COMPRESSOR_STACK,
			EmiTextures.IMPLOSION_COMPRESSING, EmiRecipeSorting.compareOutputThenInput());
	public static final EmiRecipeCategory INDUSTRIAL_ELECTROLYZER_CATEGORY =
		new EmiRecipeCategory(trId("industrial_electrolyzer"), INDUSTRIAL_ELECTROLYZER_STACK,
			EmiTextures.ELECTROLYZING, EmiRecipeSorting.compareOutputThenInput());
	public static final EmiRecipeCategory INDUSTRIAL_GRINDER_CATEGORY =
		new EmiRecipeCategory(trId("industrial_grinder"), INDUSTRIAL_GRINDER_STACK, EmiTextures.INDUSTRIAL_GRINDING,
			EmiRecipeSorting.compareOutputThenInput());
	public static final EmiRecipeCategory INDUSTRIAL_SAWMILL_CATEGORY =
		new EmiRecipeCategory(trId("industrial_sawmill"), INDUSTRIAL_SAWMILL_STACK, EmiTextures.SAWMILLING,
			EmiRecipeSorting.compareOutputThenInput());
	public static final EmiRecipeCategory SCRAPBOX_CATEGORY =
		new EmiRecipeCategory(trId("scrapbox"), SCRAP_BOX_STACK, EmiTextures.SCRAPBOX,
			EmiRecipeSorting.compareOutputThenInput());
	public static final EmiRecipeCategory VACUUM_FREEZER_CATEGORY =
		new EmiRecipeCategory(trId("vacuum_freezer"), VACUUM_FREEZER_STACK, EmiTextures.VACUUM_FREEZING,
			EmiRecipeSorting.compareOutputThenInput());
	public static final EmiRecipeCategory FLUID_REPLICATOR_CATEGORY =
		new EmiRecipeCategory(trId("fluid_replicator"), FLUID_REPLICATOR_STACK, EmiTextures.FLUID_REPLICATING,
			EmiRecipeSorting.compareOutputThenInput());
	public static final EmiRecipeCategory FUSION_REACTOR_CATEGORY =
		new EmiRecipeCategory(trId("fusion_reactor"), FUSION_CONTROL_COMPUTER_STACK, EmiTextures.FUSION_REACTOR,
			EmiRecipeSorting.compareOutputThenInput());
	public static final EmiRecipeCategory ROLLING_MACHINE_CATEGORY =
		new EmiRecipeCategory(trId("rolling_machine"), ROLLING_MACHINE_STACK, EmiTextures.ROLLING_MACHINE,
			EmiRecipeSorting.compareOutputThenInput());
	public static final EmiRecipeCategory SOLID_CANNING_MACHINE_CATEGORY =
		new EmiRecipeCategory(trId("solid_canning_machine"), SOLID_CANNING_MACHINE_STACK, EmiTextures.CANNING,
			EmiRecipeSorting.compareOutputThenInput());
	public static final EmiRecipeCategory WIRE_MILL_CATEGORY =
		new EmiRecipeCategory(trId("wire_mill"), WIRE_MILL_STACK, EmiTextures.WIRE_MILLING,
			EmiRecipeSorting.compareOutputThenInput());
	public static final EmiStack LATHE_STACK = EmiStack.of(TRContent.Machine.LATHE);
	public static final EmiStack LARGE_LATHE_STACK = EmiStack.of(TRContent.Machine.LARGE_LATHE);
	public static final EmiStack LARGE_COMPRESSOR_STACK = EmiStack.of(TRContent.Machine.LARGE_COMPRESSOR);
	public static final EmiStack LARGE_WIRE_MILL_STACK = EmiStack.of(TRContent.Machine.LARGE_WIRE_MILL);
	public static final EmiStack LARGE_GRINDER_STACK = EmiStack.of(TRContent.Machine.LARGE_GRINDER);
	public static final EmiStack PRIMITIVE_DISTILLATION_TOWER_STACK = EmiStack.of(TRContent.Machine.PRIMITIVE_DISTILLATION_TOWER);
	public static final EmiStack ROTARY_HEARTH_FURNACE_STACK = EmiStack.of(TRContent.Machine.ROTARY_HEARTH_FURNACE);
	public static final EmiStack FURNACE_PRO_MAX_STACK = EmiStack.of(TRContent.Machine.FURNACE_PRO_MAX);
	public static final EmiStack PRECISE_ASSEMBLER_STACK = EmiStack.of(TRContent.Machine.PRECISE_ASSEMBLER);
	public static final EmiRecipeCategory LATHE_CATEGORY =
		new EmiRecipeCategory(trId("lathe"), LATHE_STACK, EmiTextures.LATHE,
			EmiRecipeSorting.compareOutputThenInput());
	public static final EmiRecipeCategory PRECISE_ASSEMBLER_CATEGORY =
		new EmiRecipeCategory(trId("precise_assembler"), PRECISE_ASSEMBLER_STACK, EmiTextures.PRECISE_ASSEMBLER,
			EmiRecipeSorting.compareOutputThenInput());

	public static final EmiRecipeCategory THERMAL_GENERATOR_CATEGORY =
		new EmiRecipeCategory(trId("thermal_generator"), THERMAL_GENERATOR_STACK, THERMAL_GENERATOR_STACK,
			EmiRecipeSorting.compareInputThenOutput());
	public static final EmiRecipeCategory GAS_TURBINE_CATEGORY =
		new EmiRecipeCategory(trId("gas_turbine"), GAS_TURBINE_STACK, GAS_TURBINE_STACK,
			EmiRecipeSorting.compareInputThenOutput());
	public static final EmiRecipeCategory DIESEL_GENERATOR_CATEGORY =
		new EmiRecipeCategory(trId("diesel_generator"), DIESEL_GENERATOR_STACK, DIESEL_GENERATOR_STACK,
			EmiRecipeSorting.compareInputThenOutput());
	public static final EmiRecipeCategory SEMI_FLUID_GENERATOR_CATEGORY =
		new EmiRecipeCategory(trId("semi_fluid_generator"), SEMI_FLUID_GENERATOR_STACK, SEMI_FLUID_GENERATOR_STACK,
			EmiRecipeSorting.compareInputThenOutput());
	public static final EmiRecipeCategory PLASMA_GENERATOR_CATEGORY =
		new EmiRecipeCategory(trId("plasma_generator"), PLASMA_GENERATOR_STACK, PLASMA_GENERATOR_STACK,
			EmiRecipeSorting.compareInputThenOutput());

	public static final EmiRecipeCategory LARGE_CHEMICAL_REACTOR_CATEGORY =
		new EmiRecipeCategory(trId("large_chemical_reactor"), LARGE_CHEMICAL_REACTOR_STACK,
			EmiTextures.LARGE_CHEMICAL_REACTOR, EmiRecipeSorting.compareOutputThenInput());

	public static final EmiRecipeCategory FLUID_FROM_CONTAINER_CATEGORY =
		new EmiRecipeCategory(trId("fluid_from_container"), EmiStack.of(Items.BUCKET));
	public static final EmiRecipeCategory FLUID_INTO_CONTAINER_CATEGORY =
		new EmiRecipeCategory(trId("fluid_into_container"), EmiStack.of(Items.WATER_BUCKET));

	public static final EmiRecipeCategory MULTIBLOCK_INFO_CATEGORY =
		new EmiRecipeCategory(trId("multiblock_info"), EmiStack.of(TRContent.MULTIBLOCK_BUILDER));

	/**
	 * Maps every JSON multiblock definition id to its controller machine, used
	 * to build the multiblock info page.
	 */
	private static final Map<String, TRContent.Machine> MULTIBLOCK_MACHINES = Map.ofEntries(
		Map.entry("large_chemical_reactor", TRContent.Machine.LARGE_CHEMICAL_REACTOR),
		Map.entry("distillation_tower", TRContent.Machine.DISTILLATION_TOWER),
		Map.entry("fluid_replicator", TRContent.Machine.FLUID_REPLICATOR),
		Map.entry("implosion_compressor", TRContent.Machine.IMPLOSION_COMPRESSOR),
		Map.entry("industrial_blast_furnace", TRContent.Machine.INDUSTRIAL_BLAST_FURNACE),
		Map.entry("industrial_grinder", TRContent.Machine.INDUSTRIAL_GRINDER),
		Map.entry("industrial_sawmill", TRContent.Machine.INDUSTRIAL_SAWMILL),
		Map.entry("vacuum_freezer", TRContent.Machine.VACUUM_FREEZER),
		Map.entry("rotary_hearth_furnace", TRContent.Machine.ROTARY_HEARTH_FURNACE),
		Map.entry("large_compressor", TRContent.Machine.LARGE_COMPRESSOR),
		Map.entry("large_wire_mill", TRContent.Machine.LARGE_WIRE_MILL),
		Map.entry("large_grinder", TRContent.Machine.LARGE_GRINDER),
		Map.entry("primitive_distillation_tower", TRContent.Machine.PRIMITIVE_DISTILLATION_TOWER),
		Map.entry("large_lathe", TRContent.Machine.LARGE_LATHE),
		Map.entry("furnace_pro_max", TRContent.Machine.FURNACE_PRO_MAX),
		Map.entry("precise_assembler", TRContent.Machine.PRECISE_ASSEMBLER));

	@Override
	public void register(EmiRegistry registry) {
		// Alloy Smelting
		registry.addCategory(ALLOY_SMELTER_CATEGORY);
		registry.addWorkstation(ALLOY_SMELTER_CATEGORY, ALLOY_SMELTER_STACK);
		registry.addWorkstation(ALLOY_SMELTER_CATEGORY, IRON_ALLOY_FURNACE_STACK);
		for (var recipe : getRecipes(registry, ModRecipes.ALLOY_SMELTER)) {
			registry.addRecipe(new SimpleTwoInputEmiRecipe(recipe, ALLOY_SMELTER_CATEGORY, 1));
		}

		// Assembling
		registry.addCategory(ASSEMBLING_MACHINE_CATEGORY);
		registry.addWorkstation(ASSEMBLING_MACHINE_CATEGORY, ASSEMBLY_MACHINE_STACK);
		for (var recipe : getRecipes(registry, ModRecipes.ASSEMBLING_MACHINE)) {
			registry.addRecipe(new AssemblingMachineEmiRecipe(recipe));
		}

		// Blast Furnace
		registry.addCategory(BLAST_FURNACE_CATEGORY);
		registry.addWorkstation(BLAST_FURNACE_CATEGORY, BLAST_FURNACE_STACK);
		registry.addWorkstation(BLAST_FURNACE_CATEGORY, ROTARY_HEARTH_FURNACE_STACK);
		for (var recipe : getRecipes(registry, ModRecipes.BLAST_FURNACE)) {
			registry.addRecipe(new BlastFurnaceEmiRecipe(recipe));
		}

		// Centrifuge
		registry.addCategory(CENTRIFUGE_CATEGORY);
		registry.addWorkstation(CENTRIFUGE_CATEGORY, INDUSTRIAL_CENTRIFUGE_STACK);
		for (var recipe : getRecipes(registry, ModRecipes.CENTRIFUGE)) {
			registry.addRecipe(new CentrifugeEmiRecipe(recipe));
		}

		// Chemical Reacting (small)
		registry.addCategory(CHEMICAL_REACTOR_CATEGORY);
		registry.addWorkstation(CHEMICAL_REACTOR_CATEGORY, CHEMICAL_REACTOR_STACK);
		registry.addWorkstation(CHEMICAL_REACTOR_CATEGORY, LARGE_CHEMICAL_REACTOR_STACK);
		for (var recipe : getRecipes(registry, ModRecipes.CHEMICAL_REACTOR)) {
			registry.addRecipe(new SimpleTwoInputEmiRecipe(recipe, CHEMICAL_REACTOR_CATEGORY, 10));
		}

		// Large Chemical Reactor
		registry.addCategory(LARGE_CHEMICAL_REACTOR_CATEGORY);
		registry.addWorkstation(LARGE_CHEMICAL_REACTOR_CATEGORY, LARGE_CHEMICAL_REACTOR_STACK);
		for (var recipe : getRecipes(registry, ModRecipes.LARGE_CHEMICAL_REACTOR)) {
			registry.addRecipe(new LargeChemicalReactorEmiRecipe(recipe));
		}

		// Compressing
		registry.addCategory(COMPRESSOR_CATEGORY);
		registry.addWorkstation(COMPRESSOR_CATEGORY, COMPRESSOR_STACK);
		registry.addWorkstation(COMPRESSOR_CATEGORY, LARGE_COMPRESSOR_STACK);
		for (var recipe : getRecipes(registry, ModRecipes.COMPRESSOR)) {
			registry.addRecipe(new SimpleOneInputEmiRecipe(recipe, COMPRESSOR_CATEGORY, 1));
		}

		// Distillation Tower
		registry.addCategory(DISTILLATION_TOWER_CATEGORY);
		registry.addWorkstation(DISTILLATION_TOWER_CATEGORY, DISTILLATION_TOWER_STACK);
		registry.addWorkstation(DISTILLATION_TOWER_CATEGORY, PRIMITIVE_DISTILLATION_TOWER_STACK);
		for (var recipe : getRecipes(registry, ModRecipes.DISTILLATION_TOWER)) {
			registry.addRecipe(new DistillationTowerEmiRecipe(recipe));
		}

		// Extracting
		registry.addCategory(EXTRACTOR_CATEGORY);
		registry.addWorkstation(EXTRACTOR_CATEGORY, EXTRACTOR_STACK);
		for (var recipe : getRecipes(registry, ModRecipes.EXTRACTOR)) {
			registry.addRecipe(new SimpleOneInputEmiRecipe(recipe, EXTRACTOR_CATEGORY, 1));
		}

		// Grinding
		registry.addCategory(GRINDER_CATEGORY);
		registry.addWorkstation(GRINDER_CATEGORY, GRINDER_STACK);
		registry.addWorkstation(GRINDER_CATEGORY, INDUSTRIAL_GRINDER_STACK);
		registry.addWorkstation(GRINDER_CATEGORY, LARGE_GRINDER_STACK);
		for (var recipe : getRecipes(registry, ModRecipes.GRINDER)) {
			registry.addRecipe(new SimpleOneInputEmiRecipe(recipe, GRINDER_CATEGORY, 1));
		}

		// Implosion Compressor
		registry.addCategory(IMPLOSION_COMPRESSOR_CATEGORY);
		registry.addWorkstation(IMPLOSION_COMPRESSOR_CATEGORY, IMPLOSION_COMPRESSOR_STACK);
		for (var recipe : getRecipes(registry, ModRecipes.IMPLOSION_COMPRESSOR)) {
			registry.addRecipe(new ImplosionCompressorEmiRecipe(recipe));
		}

		// Industrial Electrolyzing
		registry.addCategory(INDUSTRIAL_ELECTROLYZER_CATEGORY);
		registry.addWorkstation(INDUSTRIAL_ELECTROLYZER_CATEGORY, INDUSTRIAL_ELECTROLYZER_STACK);
		for (var recipe : getRecipes(registry, ModRecipes.INDUSTRIAL_ELECTROLYZER)) {
			registry.addRecipe(new IndustrialElectrolyzerEmiRecipe(recipe));
		}

		// Industrial Grinding
		registry.addCategory(INDUSTRIAL_GRINDER_CATEGORY);
		registry.addWorkstation(INDUSTRIAL_GRINDER_CATEGORY, INDUSTRIAL_GRINDER_STACK);
		LongHolder grinderCapacityHolder = new LongHolder(1000 * 81);
		for (var recipe : getRecipes(registry, ModRecipes.INDUSTRIAL_GRINDER)) {
			registry.addRecipe(new IndustrialGrinderEmiRecipe(recipe, grinderCapacityHolder));
		}

		// Industrial Sawmilling
		registry.addCategory(INDUSTRIAL_SAWMILL_CATEGORY);
		registry.addWorkstation(INDUSTRIAL_SAWMILL_CATEGORY, INDUSTRIAL_SAWMILL_STACK);
		LongHolder sawmillCapacityHolder = new LongHolder(1000 * 81);
		for (var recipe : getRecipes(registry, ModRecipes.INDUSTRIAL_SAWMILL)) {
			registry.addRecipe(new IndustrialSawmillEmiRecipe(recipe, sawmillCapacityHolder));
		}

		// Scrapbox
		registry.addCategory(SCRAPBOX_CATEGORY);
		registry.addWorkstation(SCRAPBOX_CATEGORY, SCRAPBOXINATOR_STACK);
		for (var recipe : getRecipes(registry, ModRecipes.SCRAPBOX)) {
			registry.addRecipe(new SimpleOneInputEmiRecipe(recipe, SCRAPBOX_CATEGORY, 1));
		}

		// Vacuum Freezing
		registry.addCategory(VACUUM_FREEZER_CATEGORY);
		registry.addWorkstation(VACUUM_FREEZER_CATEGORY, VACUUM_FREEZER_STACK);
		for (var recipe : getRecipes(registry, ModRecipes.VACUUM_FREEZER)) {
			registry.addRecipe(new SimpleOneInputEmiRecipe(recipe, VACUUM_FREEZER_CATEGORY, 64));
		}

		// Fluid Replicating
		registry.addCategory(FLUID_REPLICATOR_CATEGORY);
		registry.addWorkstation(FLUID_REPLICATOR_CATEGORY, FLUID_REPLICATOR_STACK);
		LongHolder replicatorCapacityHolder = new LongHolder(1000 * 81);
		for (var recipe : getRecipes(registry, ModRecipes.FLUID_REPLICATOR)) {
			registry.addRecipe(new FluidReplicatorEmiRecipe(recipe, replicatorCapacityHolder));
		}

		// Fusion Reactor
		registry.addCategory(FUSION_REACTOR_CATEGORY);
		registry.addWorkstation(FUSION_REACTOR_CATEGORY, FUSION_CONTROL_COMPUTER_STACK);
		for (var recipe : getRecipes(registry, ModRecipes.FUSION_REACTOR)) {
			registry.addRecipe(new FusionReactorEmiRecipe(recipe));
		}

		// Rolling Machine
		registry.addCategory(ROLLING_MACHINE_CATEGORY);
		registry.addWorkstation(ROLLING_MACHINE_CATEGORY, ROLLING_MACHINE_STACK);
		for (var recipe : getRecipes(registry, ModRecipes.ROLLING_MACHINE)) {
			registry.addRecipe(new RollingMachineEmiRecipe(recipe));
		}

		// Solid Canning
		registry.addCategory(SOLID_CANNING_MACHINE_CATEGORY);
		registry.addWorkstation(SOLID_CANNING_MACHINE_CATEGORY, SOLID_CANNING_MACHINE_STACK);
		for (var recipe : getRecipes(registry, ModRecipes.SOLID_CANNING_MACHINE)) {
			registry.addRecipe(new SimpleTwoInputEmiRecipe(recipe, SOLID_CANNING_MACHINE_CATEGORY, 1));
		}

		// Wire Milling
		registry.addCategory(WIRE_MILL_CATEGORY);
		registry.addWorkstation(WIRE_MILL_CATEGORY, WIRE_MILL_STACK);
		registry.addWorkstation(WIRE_MILL_CATEGORY, LARGE_WIRE_MILL_STACK);
		for (var recipe : getRecipes(registry, ModRecipes.WIRE_MILL)) {
			registry.addRecipe(new SimpleOneInputEmiRecipe(recipe, WIRE_MILL_CATEGORY, 1));
		}

		// Lathe
		registry.addCategory(LATHE_CATEGORY);
		registry.addWorkstation(LATHE_CATEGORY, LATHE_STACK);
		registry.addWorkstation(LATHE_CATEGORY, LARGE_LATHE_STACK);
		for (var recipe : getRecipes(registry, ModRecipes.LATHE)) {
			registry.addRecipe(new SimpleOneInputEmiRecipe(recipe, LATHE_CATEGORY, 1));
		}

		// Precise Assembler
		registry.addCategory(PRECISE_ASSEMBLER_CATEGORY);
		registry.addWorkstation(PRECISE_ASSEMBLER_CATEGORY, PRECISE_ASSEMBLER_STACK);
		for (var recipe : getRecipes(registry, ModRecipes.PRECISE_ASSEMBLER)) {
			registry.addRecipe(new PreciseAssemblerEmiRecipe(recipe));
		}

		// Generators
		registry.addCategory(THERMAL_GENERATOR_CATEGORY);
		registry.addWorkstation(THERMAL_GENERATOR_CATEGORY, THERMAL_GENERATOR_STACK);
		for (var recipe : getRecipes(registry, ModRecipes.THERMAL_GENERATOR)) {
			registry.addRecipe(new FluidGeneratorEmiRecipe(recipe, THERMAL_GENERATOR_CATEGORY, 10, 1000000));
		}

		registry.addCategory(GAS_TURBINE_CATEGORY);
		registry.addWorkstation(GAS_TURBINE_CATEGORY, GAS_TURBINE_STACK);
		for (var recipe : getRecipes(registry, ModRecipes.GAS_GENERATOR)) {
			registry.addRecipe(new FluidGeneratorEmiRecipe(recipe, GAS_TURBINE_CATEGORY, 10, 1000000));
		}

		registry.addCategory(DIESEL_GENERATOR_CATEGORY);
		registry.addWorkstation(DIESEL_GENERATOR_CATEGORY, DIESEL_GENERATOR_STACK);
		for (var recipe : getRecipes(registry, ModRecipes.DIESEL_GENERATOR)) {
			registry.addRecipe(new FluidGeneratorEmiRecipe(recipe, DIESEL_GENERATOR_CATEGORY, 10, 10000));
		}

		registry.addCategory(SEMI_FLUID_GENERATOR_CATEGORY);
		registry.addWorkstation(SEMI_FLUID_GENERATOR_CATEGORY, SEMI_FLUID_GENERATOR_STACK);
		for (var recipe : getRecipes(registry, ModRecipes.SEMI_FLUID_GENERATOR)) {
			registry.addRecipe(new FluidGeneratorEmiRecipe(recipe, SEMI_FLUID_GENERATOR_CATEGORY, 10, 1000000));
		}

		registry.addCategory(PLASMA_GENERATOR_CATEGORY);
		registry.addWorkstation(PLASMA_GENERATOR_CATEGORY, PLASMA_GENERATOR_STACK);
		for (var recipe : getRecipes(registry, ModRecipes.PLASMA_GENERATOR)) {
			registry.addRecipe(new FluidGeneratorEmiRecipe(recipe, PLASMA_GENERATOR_CATEGORY, 10, 500000000));
		}

		// Add machines that do vanilla things
		registry.addWorkstation(VanillaEmiRecipeCategories.CRAFTING, AUTO_CRAFTING_TABLE_STACK);
		registry.addWorkstation(VanillaEmiRecipeCategories.SMELTING, IRON_FURNACE_STACK);
		registry.addWorkstation(VanillaEmiRecipeCategories.SMELTING, ELECTRIC_FURNACE_STACK);
		registry.addWorkstation(VanillaEmiRecipeCategories.SMELTING, FURNACE_PRO_MAX_STACK);

		// Multiblock structure info: every JSON-defined machine lists the
		// blocks it needs (block × count) as inputs and its controller as output.
		registry.addCategory(MULTIBLOCK_INFO_CATEGORY);
		for (Map.Entry<String, TRContent.Machine> entry : MULTIBLOCK_MACHINES.entrySet()) {
			MultiblockDefinition definition = MultiblockDefinitionLoader.get(entry.getKey());
			if (definition == null) {
				continue;
			}
			registry.addRecipe(new MultiblockInfoEmiRecipe(entry.getKey(), definition,
				TRIntegration.stackOf(entry.getValue())));
		}

		// Fluid ↔ Cell container recipes
		registry.addCategory(FLUID_FROM_CONTAINER_CATEGORY);
		registry.addCategory(FLUID_INTO_CONTAINER_CATEGORY);

		// Cells should be compared with NBT data
		registry.setDefaultComparison(CELL, Comparison.compareComponents());

		// Fluid into and from Cells
		Identifier cellId = CELL.getId();
		for (Fluid fluid : Registries.FLUID) {

			if (!fluid.isStill(fluid.getDefaultState())) {
				continue;
			}

		Identifier fluidId = Registries.FLUID.getId(fluid);
			EmiStack fluidStack = EmiStack.of(fluid, 1000 * 81);
			EmiStack fluidCellStack = EmiStack.of(DynamicCellItem.getCellWithFluid(fluid));

			Identifier fromId =
				Identifier.of("techreborn",
					"/fluid_from_container/extra_mod_integrations_core/" + cellId.getNamespace() + "/" +
						cellId.getNamespace() + "/" + fluidId.getNamespace() + "/" + fluidId.getPath());
			registry.addRecipe(new techreborn.client.compat.emi.core.FluidFromContainerEmiRecipe(fromId, fluidStack, fluidCellStack, CELL));
			Identifier intoId =
				Identifier.of("techreborn",
					"/fluid_into_container/extra_mod_integrations_core/" + cellId.getNamespace() + "/" +
						cellId.getNamespace() + "/" + fluidId.getNamespace() + "/" + fluidId.getPath());
			registry.addRecipe(new techreborn.client.compat.emi.core.FluidIntoContainerEmiRecipe(intoId, fluidStack, CELL, fluidCellStack));
		}
	}

	public static Identifier trId(String path) {
		return Identifier.of("techreborn", path);
	}

	@SuppressWarnings("unchecked")
	private static <T extends net.minecraft.recipe.Recipe<?>> List<RecipeEntry<T>>
		getRecipes(EmiRegistry registry, RecipeType<T> type) {
		List<RecipeEntry<T>> list = new java.util.ArrayList<>();
		for (RecipeEntry<?> entry : registry.getRecipeManager().values()) {
			if (entry.value().getType() == type) {
				list.add((RecipeEntry<T>) entry);
			}
		}
		return list;
	}
}
