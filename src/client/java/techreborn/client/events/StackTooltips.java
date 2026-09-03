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

package techreborn.client.events;

import net.minecraft.block.Block;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import reborncore.common.blockentity.MachineBaseBlockEntity;
import reborncore.common.powerSystem.PowerAcceptorBlockEntity;
import reborncore.common.recipes.RecipeCrafter;
import techreborn.blockentity.machine.GenericMachineBlockEntity;

import java.util.List;

/**
 * Always-visible machine stat lines appended to the item tooltip. The
 * values come from the {@link IListInfoProvider} block entity of the
 * stack's block, so placed machines and their picker items report the same
 * capacity, max input/output and (where applicable) generation or transfer
 * rates.
 * <p>
 * Recipe types and proxy multipliers of multiblock machines are rendered
 * by the machine blocks themselves ({@code appendMachineTooltip}); this
 * class adds the energy block plus the built-in crafting time multiplier
 * of machines such as the Kroll Reduction Vessel.
 */
public final class StackTooltips {

	private StackTooltips() {
	}

	/**
	 * Appends the machine stat block for TR machine blocks.
	 */
	public static void addMachineStats(ItemStack stack, Item.TooltipContext context, List<Text> lines) {
		Block block = Block.getBlockFromItem(stack.getItem());
		if (!(block instanceof BlockWithEntity blockWithEntity)) {
			return;
		}

		BlockEntity blockEntity;
		try {
			blockEntity = createPreviewEntity(stack, context);
		} catch (Exception e) {
			// A preview entity must never crash the tooltip.
			return;
		}
		if (!(blockEntity instanceof MachineBaseBlockEntity machine)) {
			return;
		}

		try {
			// Skip machines without any energy functionality (e.g. the Dyson
			// Swarm host): their zeroed "Max Energy: 0" line is just noise.
			if (machine instanceof PowerAcceptorBlockEntity acceptor
					&& acceptor.getMaxStoredPower() <= 0
					&& acceptor.getMaxInput(null) <= 0
					&& acceptor.getMaxOutput(null) <= 0) {
				return;
			}

			machine.addInfo(lines, false, false);

			if (machine instanceof GenericMachineBlockEntity genericMachine && genericMachine.crafter != null) {
				addTimeMultiplier(genericMachine.crafter, lines);
			}
		} catch (Exception e) {
			// A preview entity must never crash the tooltip.
		}
	}

	/**
	 * Built-in crafting time multiplier. Upgrades aside, a plain
	 * {@link RecipeCrafter} reports zero, so only machines with a fixed
	 * speed-up (Kroll Reduction Vessel and friends) get a line.
	 */
	private static void addTimeMultiplier(RecipeCrafter crafter, List<Text> lines) {
		double speed = crafter.getSpeedMultiplier();
		if (speed <= 0) {
			return;
		}
		double multiplier = 1.0 - speed;
		ToolTipAssistUtils.addStat(lines, "techreborn.tooltip.machine.time_multiplier",
				(multiplier == Math.floor(multiplier) ? String.valueOf((long) multiplier) : String.valueOf(multiplier)) + "x");
	}

	/**
	 * Creates the block entity of the machine block, reading item NBT so
	 * machines placed from a picker item report their configured values.
	 * The preview entity is never added to a world; the client world (or
	 * none) is only attached so stat getters stay on safe code paths.
	 * Returns {@code null} when the block has no block entity or
	 * construction touched the world.
	 */
	private static BlockEntity createPreviewEntity(ItemStack stack, Item.TooltipContext context) {
		Block block = Block.getBlockFromItem(stack.getItem());
		if (!(block instanceof BlockWithEntity blockWithEntity)) {
			return null;
		}

		NbtCompound nbt = stack.getOrDefault(DataComponentTypes.BLOCK_ENTITY_DATA, NbtComponent.DEFAULT).getNbt();
		RegistryWrapper.WrapperLookup lookup = context.getRegistryLookup();

		BlockEntity blockEntity;
		if (nbt != null && nbt.contains("id", NbtElement.STRING_TYPE)) {
			blockEntity = BlockEntity.createFromNbt(BlockPos.ORIGIN, block.getDefaultState(), nbt, lookup);
		} else {
			blockEntity = blockWithEntity.createBlockEntity(BlockPos.ORIGIN, block.getDefaultState());
		}

		if (blockEntity != null) {
			World world = MinecraftClient.getInstance().world;
			blockEntity.setWorld(world);
		}
		return blockEntity;
	}
}
