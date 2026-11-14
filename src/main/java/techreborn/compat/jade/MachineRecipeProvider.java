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
import net.minecraft.util.math.BlockPos;
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
import techreborn.api.IEnergyProducerProvider;
import techreborn.blockentity.generator.multiblock.LargeFluidGeneratorBlockEntity;
import techreborn.blockentity.machine.multiblock.DysonSwarmMachineBlockEntity;
import techreborn.blockentity.machine.multiblock.IsotopeSeparatorBlockEntity;
import techreborn.blockentity.machine.multiblock.NaquadahDeresonatorBlockEntity;
import techreborn.blockentity.machine.multiblock.PrimitiveDistillationTowerBlockEntity;
import techreborn.blockentity.machine.multiblock.VoidDrillBlockEntity;
import techreborn.blockentity.machine.multiblock.SpaceElevatorAssemblerBlockEntity;
import techreborn.blockentity.machine.multiblock.SpaceElevatorMinerBlockEntity;
import techreborn.blockentity.machine.multiblock.SpaceElevatorBlockEntity;
import techreborn.blockentity.machine.multiblock.JsonMultiblockMachineBlockEntity;
import techreborn.space.SpaceTerminalService;
import techreborn.space.SpaceProgressState;
import techreborn.space.data.PlanetCatalog;
import techreborn.space.task.SpaceTaskEngine;
import techreborn.team.TeamScopes;
import net.minecraft.server.network.ServerPlayerEntity;

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

	/** GTCEu style voltage tiers (EU/t), low to high. */
	private static final long[] VOLTAGES = {8, 32, 128, 512, 2048, 8192, 32768, 131072, 524288};
	private static final String[] TIER_NAMES = {"ULV", "LV", "MV", "HV", "EV", "IV", "LuV", "ZPM", "UV"};
	/** GTCEu voltage colours (VCM order): ULV, LV, MV, HV, EV, IV, LuV, ZPM, UV. */
	private static final Formatting[] TIER_COLORS = {
			Formatting.DARK_GRAY, Formatting.GRAY, Formatting.AQUA, Formatting.GOLD,
			Formatting.DARK_PURPLE, Formatting.BLUE, Formatting.LIGHT_PURPLE, Formatting.RED, Formatting.DARK_AQUA
	};

	// ---- Server side: sync recipe state ----

	@Override
	public void appendServerData(NbtCompound data, BlockAccessor accessor) {
		// Space tasks have no RecipeCrafter. A viewer-specific fresh response is
		// mandatory: neither public BE NBT nor another viewer's snapshot is a source.
		if (accessor.getBlockEntity() instanceof SpaceElevatorBlockEntity
				|| accessor.getBlockEntity() instanceof SpaceElevatorMinerBlockEntity) {
			data.remove(SpaceTaskJade.DATA_KEY);
			data.remove("elevator_host");
			for (String field : List.of("inputs", "outputs", "power", "parallel", "maxParallel", "tickTime", "neededTicks")) {
				data.remove(field);
			}
			if (accessor.getPlayer() instanceof ServerPlayerEntity viewer
					&& accessor.getBlockEntity() instanceof JsonMultiblockMachineBlockEntity machine
					&& machine.getWorld() == viewer.getServerWorld()
					&& machine.getWorld() == accessor.getLevel()
					&& !machine.isRemoved() && machine.getWorld().getBlockEntity(machine.getPos()) == machine) {
				var ownership = SpaceTerminalService.state(machine);
				NbtCompound task = SpaceTaskJade.snapshot(ownership, SpaceTaskEngine.container(machine), TeamScopes.scopeOf(viewer),
						accessor.getLevel().getRegistryManager(), bodyId -> {
							var body = PlanetCatalog.get(bodyId);
							int stage = SpaceProgressState.get(viewer.getServerWorld()).stage(ownership.ownerScope(), bodyId);
							return body != null && SpaceTerminalService.visible(body.visibility().name(), stage)
									? body.display().nameKey() : "gui.techreborn.space.unknown_body";
						}, SpaceTaskEngine.container(machine).consumed(viewer.getServer().getTicks()));
				if (!task.isEmpty()) {
					data.put(SpaceTaskJade.DATA_KEY, task);
					if (machine instanceof SpaceElevatorMinerBlockEntity miner) {
						data.putLong("elevator_host", miner.getSyncedHostPos());
					}
				}
			}
			return;
		}
		// Generator output (EU/t)
		if (accessor.getBlockEntity() instanceof IEnergyProducerProvider producer) {
			data.putLong("output", producer.getCurrentOutputPerTick());
		}

		// Compressed air boost of the large multiblock generators
		if (accessor.getBlockEntity() instanceof LargeFluidGeneratorBlockEntity fluidGenerator
				&& fluidGenerator.isOxygenBoosted()) {
			data.putBoolean("oxygen_boosted", true);
		}


		// Dyson Swarm binding (host and receiver)
		if (accessor.getBlockEntity() instanceof DysonSwarmMachineBlockEntity dyson) {
			data.putString("dyson_bound", dyson.getBoundPlayerName());
		}

		// Space Elevator unit -> host binding
		if (accessor.getBlockEntity() instanceof SpaceElevatorAssemblerBlockEntity assembler) {
			data.putLong("elevator_host", assembler.getSyncedHostPos());
		} else if (accessor.getBlockEntity() instanceof SpaceElevatorMinerBlockEntity miner) {
			data.putLong("elevator_host", miner.getSyncedHostPos());
		}

		// Isotope Separator: speed drive state, cost multipliers and rotor life.
		// This machine alone reports these, so the lines stay out of every other
		// machine's tooltip.
		if (accessor.getBlockEntity() instanceof IsotopeSeparatorBlockEntity separator) {
			double rpm = separator.getRpm();
			data.putDouble("iso_rpm", rpm);
			data.putDouble("iso_limit", separator.getRotorRpmLimit());
			data.putDouble("iso_energy", separator.energyMultiplier(rpm));
			data.putDouble("iso_time", separator.timeMultiplier(rpm));
			data.putDouble("iso_success", separator.successRate(rpm));
			ItemStack rotor = separator.getRotorStack();
			data.putBoolean("iso_has_rotor", separator.isRotorInstalled());
			data.putInt("iso_rotor_damage", rotor.isEmpty() ? 0 : rotor.getDamage());
			data.putInt("iso_rotor_max", rotor.isEmpty() ? 0 : rotor.getMaxDamage());
			data.putLong("iso_rotor_ticks", separator.getRotorRemainingTicks());
		}

		// Naquadah Deresonator: the chance the run in progress will pay out, plus
		// the fluid it has already fed into that chance.
		if (accessor.getBlockEntity() instanceof NaquadahDeresonatorBlockEntity deresonator) {
			data.putDouble("deresonator_chance", deresonator.getCurrentSuccessRate());
			data.putLong("deresonator_fluid", deresonator.getRunFluidMb());
		}

		// Void Drill: how much the chunk has left and how fast it is being drained.
		if (accessor.getBlockEntity() instanceof VoidDrillBlockEntity drill) {
			data.putLong("drill_reserve", drill.getChunkRemaining());
			data.putDouble("drill_depletion", drill.depletionFactor());
		}

		// Primitive Distillation Tower: fouling state.
		if (accessor.getBlockEntity() instanceof PrimitiveDistillationTowerBlockEntity tower) {
			data.putInt("tower_impurity", tower.getImpurity());
			data.putBoolean("tower_blocked", tower.isBlocked());
		}

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

		data.putInt("power", (int) crafter.getEuPerTick(recipe.power()));
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
		IElementHelper helper = IElementHelper.get();
		RegistryWrapper.WrapperLookup lookup = accessor.getLevel().getRegistryManager();

		// Generator production line (if producing). Must come before the recipe
		// check below, because generators have no crafter and therefore no
		// "parallel" field in the server data.
		if (data.contains("output") && data.getLong("output") > 0) {
			long output = data.getLong("output");
			tooltip.add(Text.translatable("jade.techreborn.generation",
					Text.literal(String.valueOf(output)).formatted(Formatting.YELLOW),
					formatVoltage(output)));
		}

		// Compressed air boost state of the large multiblock generators
		if (data.getBoolean("oxygen_boosted")) {
			tooltip.add(Text.translatable("jade.techreborn.oxygen_boosted").formatted(Formatting.GREEN));
		}

		// Dyson Swarm binding line (host and receiver)
		if (data.contains("dyson_bound")) {
			String boundPlayer = data.getString("dyson_bound");
			if (boundPlayer.isEmpty()) {
				tooltip.add(Text.translatable("gui.techreborn.dyson.unbound").formatted(Formatting.GRAY));
			} else {
				tooltip.add(Text.translatable("gui.techreborn.dyson.bound_to",
						Text.literal(boundPlayer).formatted(Formatting.YELLOW)));
			}
		}

		// Space Elevator unit -> host binding line
		if (data.contains("elevator_host")) {
			long hostPos = data.getLong("elevator_host");
			if (hostPos == Long.MIN_VALUE) {
				tooltip.add(Text.translatable("gui.techreborn.space_elevator.unbound").formatted(Formatting.GRAY));
			} else {
				BlockPos host = BlockPos.fromLong(hostPos);
				tooltip.add(Text.translatable("gui.techreborn.space_elevator.bound_to",
						Text.literal(String.valueOf(host.getX())).formatted(Formatting.YELLOW),
						Text.literal(String.valueOf(host.getY())).formatted(Formatting.YELLOW),
						Text.literal(String.valueOf(host.getZ())).formatted(Formatting.YELLOW)));
			}
		}

		// Space tasks render only this request's server view. Never fall back to
		// client BE fields or generic recipe data after a scope change.
		if (accessor.getBlockEntity() instanceof SpaceElevatorBlockEntity
				|| accessor.getBlockEntity() instanceof SpaceElevatorMinerBlockEntity) {
			if (data.contains(SpaceTaskJade.DATA_KEY, NbtElement.COMPOUND_TYPE)) {
				appendSpaceTask(tooltip, helper, data.getCompound(SpaceTaskJade.DATA_KEY), lookup);
			}
			return;
		}

		// Isotope Separator: speed, cost multipliers and rotor life. Shown
		// before the recipe lines so it is present even while idle.
		if (data.contains("iso_rpm")) {
			double rpm = data.getDouble("iso_rpm");
			double limit = data.getDouble("iso_limit");
			tooltip.add(Text.translatable("jade.techreborn.isotope_rpm",
					Text.literal(String.format("%.0f%%", rpm * 100.0)).formatted(Formatting.YELLOW),
					Text.literal(String.format("%.0f%%", limit * 100.0)).formatted(Formatting.YELLOW)));
			tooltip.add(Text.translatable("jade.techreborn.isotope_energy",
					Text.literal(String.format("%.2fx", data.getDouble("iso_energy"))).formatted(Formatting.YELLOW)));
			tooltip.add(Text.translatable("jade.techreborn.isotope_time",
					Text.literal(String.format("%.2fx", data.getDouble("iso_time"))).formatted(Formatting.YELLOW)));
			tooltip.add(Text.translatable("jade.techreborn.isotope_success",
					Text.literal(String.format("%.0f%%", data.getDouble("iso_success") * 100.0)).formatted(Formatting.YELLOW)));
			if (data.getBoolean("iso_has_rotor")) {
				tooltip.add(Text.translatable("jade.techreborn.isotope_rotor",
						Text.literal(String.valueOf(data.getInt("iso_rotor_max") - data.getInt("iso_rotor_damage")))
								.formatted(Formatting.YELLOW),
						Text.literal(String.valueOf(data.getInt("iso_rotor_max"))).formatted(Formatting.YELLOW)));
				long ticks = data.getLong("iso_rotor_ticks");
				tooltip.add(Text.translatable("jade.techreborn.isotope_rotor_time",
						Text.literal(IsotopeSeparatorBlockEntity.formatTicks(ticks)).formatted(Formatting.YELLOW)));
			} else {
				tooltip.add(Text.translatable("jade.techreborn.isotope_rotor_none").formatted(Formatting.GRAY));
			}
		}

		// Naquadah Deresonator: the chance the run in progress will pay out.
		if (data.contains("deresonator_chance")) {
			tooltip.add(Text.translatable("jade.techreborn.naquadah_deresonator_chance",
					Text.literal(String.format("%.2f%%", data.getDouble("deresonator_chance") * 100.0)).formatted(Formatting.YELLOW)));
			long fed = data.getLong("deresonator_fluid");
			if (fed > 0) {
				tooltip.add(Text.translatable("jade.techreborn.naquadah_deresonator_fluid",
						Text.literal(String.valueOf(fed)).formatted(Formatting.YELLOW)));
			}
		}

		// Void Drill: the chunk's remaining reserve and the current depletion.
		if (data.contains("drill_reserve")) {
			tooltip.add(Text.translatable("jade.techreborn.void_drill_reserve",
					Text.literal(String.format("%.1f B", data.getLong("drill_reserve") / 1000.0)).formatted(Formatting.YELLOW)));
			tooltip.add(Text.translatable("jade.techreborn.void_drill_depletion",
					Text.literal(String.format("%.0f%%", data.getDouble("drill_depletion") * 100.0)).formatted(Formatting.YELLOW)));
		}

		// Primitive Distillation Tower: fouling, and why it stopped.
		if (data.contains("tower_impurity")) {
			int impurity = data.getInt("tower_impurity");
			tooltip.add(Text.translatable("jade.techreborn.primitive_distillation_tower_impurity",
					Text.literal(impurity + "%").formatted(data.getBoolean("tower_blocked") ? Formatting.RED : Formatting.YELLOW)));
			if (data.getBoolean("tower_blocked")) {
				tooltip.add(Text.translatable("jade.techreborn.primitive_distillation_tower_blocked").formatted(Formatting.RED));
			}
		}

		// Recipe lines (machines with an active crafter)
		if (!data.contains("parallel")) {
			return;
		}

		// Show the total amount for the current parallel batch; hold shift to
		// see the single-recipe amounts.
		int parallel = data.getInt("parallel");
		boolean showTotal = !accessor.getPlayer().isSneaking();

		// Inputs: item icons + names
		List<IElement> inputElements = new ArrayList<>();
		for (NbtElement element : data.getList("inputs", NbtElement.COMPOUND_TYPE)) {
			decodeStack((NbtCompound) element, lookup).ifPresent(stack -> {
				inputElements.add(helper.item(stack));
				int count = stack.getCount();
				int displayCount = showTotal ? count * parallel : count;
				inputElements.add(helper.text(Text.literal(" " + stack.getName().getString() + " x" + displayCount)));
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
				int count = stack.getCount();
				int displayCount = showTotal ? count * parallel : count;
				outputElements.add(helper.text(Text.literal(" " + stack.getName().getString() + " x" + displayCount)));
			});
		}
		if (!outputElements.isEmpty()) {
			tooltip.add(outputElements);
		}

		int power = data.getInt("power");
		int totalPower = power * parallel;
		tooltip.add(Text.translatable("jade.techreborn.energy",
				Text.literal(String.valueOf(totalPower)).formatted(Formatting.YELLOW),
				formatVoltage(totalPower)));

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

	private static void appendSpaceTask(ITooltip tooltip, IElementHelper helper, NbtCompound data,
			RegistryWrapper.WrapperLookup lookup) {
		if (data.contains("targetName", NbtElement.STRING_TYPE)) {
			tooltip.add(Text.translatable("jade.techreborn.space_target", Text.translatable(data.getString("targetName")).formatted(Formatting.YELLOW)));
		}
		var state = Text.translatable("gui.techreborn.space.task_state." + data.getString("state"));
		if (!data.contains("kind", NbtElement.STRING_TYPE)) {
			tooltip.add(state.formatted(Formatting.GRAY));
			if (data.getBoolean("auto")) { tooltip.add(Text.translatable("gui.techreborn.space.auto_enabled").formatted(Formatting.GREEN)); }
			return;
		}
		tooltip.add(Text.translatable("jade.techreborn.space_task",
				Text.translatable("gui.techreborn.space.action." + data.getString("kind")).formatted(Formatting.YELLOW))
				.append(" — ").append(state));
		tooltip.add(Text.translatable("jade.techreborn.space_scheme", Text.literal(Integer.toString(data.getInt("tier"))).formatted(Formatting.YELLOW)));
		long required = Math.max(0, data.getLong("requiredPower"));
		tooltip.add(Text.translatable("jade.techreborn.space_required_energy", yellow(required), formatVoltage(required)));
		tooltip.add(Text.translatable("jade.techreborn.space_actual_energy", yellow(Math.max(0, data.getLong("actualPower")))));
		long duration = Math.max(1, data.getLong("duration"));
		long elapsed = Math.clamp(data.getLong("elapsed"), 0, duration);
		tooltip.add(helper.progress((float) Math.min(1D, (double) elapsed / duration)));
		tooltip.add(Text.translatable("jade.techreborn.progress", yellow(elapsed), yellow(duration)));
		tooltip.add(Text.translatable("jade.techreborn.space_seconds",
				Text.literal(SpaceTaskJade.seconds(elapsed)).formatted(Formatting.YELLOW),
				Text.literal(SpaceTaskJade.seconds(duration)).formatted(Formatting.YELLOW)));
		appendSpaceStacks(tooltip, helper, "jade.techreborn.space_escrow", data.getList("escrow", NbtElement.COMPOUND_TYPE), lookup);
		appendSpaceStacks(tooltip, helper, "jade.techreborn.space_outputs", data.getList("outputs", NbtElement.COMPOUND_TYPE), lookup);
		if (data.contains("pending", NbtElement.LIST_TYPE)) {
			appendSpaceStacks(tooltip, helper, data.getString("pendingKind").equals("refund")
					? "jade.techreborn.space_pending_refund" : "jade.techreborn.space_pending_output",
					data.getList("pending", NbtElement.COMPOUND_TYPE), lookup);
		}
	}

	private static Text yellow(long value) {
		return Text.literal(Long.toString(value)).formatted(Formatting.YELLOW);
	}

	private static void appendSpaceStacks(ITooltip tooltip, IElementHelper helper, String heading,
			NbtList entries, RegistryWrapper.WrapperLookup lookup) {
		tooltip.add(Text.translatable(heading).formatted(Formatting.GRAY));
		// One descriptor per row stays bounded and legible even for 64 entries.
		// Unit icons avoid a second, misleading aggregate count on the icon.
		for (ItemStack stack : SpaceTaskJade.decodeStacks(entries, lookup)) {
			tooltip.add(List.of(helper.item(stack.copyWithCount(1)),
					helper.text(Text.empty().append(" ").append(stack.getName()).append(" x" + stack.getCount()))));
		}
	}

	/**
	 * Formats an EU/t value GTCEu style: {@code X.XA TIER}, where the tier is
	 * the highest voltage tier not exceeding the value (e.g. 200 EU/t ->
	 * {@code 1.6A MV}). Values above UV clamp to UV. The text is coloured with
	 * the tier's GTCEu colour.
	 */
	private static Text formatVoltage(long euPerTick) {
		int tier = 0;
		for (int i = 0; i < VOLTAGES.length; i++) {
			if (VOLTAGES[i] <= euPerTick) {
				tier = i;
			}
		}
		double amps = (double) euPerTick / VOLTAGES[tier];
		String ampsText = String.format("%.1fA", amps);
		return Text.literal(ampsText + " " + TIER_NAMES[tier]).formatted(TIER_COLORS[tier]);
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
