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

package techreborn;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.ComposterBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reborncore.common.blockentity.RedstoneConfiguration;
import reborncore.common.config.Configuration;
import reborncore.common.recipes.RecipeCrafter;
import reborncore.common.util.Torus;
import techreborn.blockentity.GuiType;
import techreborn.blockentity.machine.multiblock.SpaceElevatorBlockEntity;
import techreborn.component.TRDataComponentTypes;
import techreborn.config.TechRebornConfig;
import techreborn.events.ApplyArmorToDamageHandler;
import techreborn.events.OreDepthSyncHandler;
import techreborn.events.UseBlockHandler;
import techreborn.init.FuelRecipes;
import techreborn.init.ModLoot;
import techreborn.init.ModRecipes;
import techreborn.init.ModSounds;
import techreborn.init.TRBlockEntities;
import techreborn.init.TRCauldronBehavior;
import techreborn.init.TRContent;
import techreborn.init.TRDispenserBehavior;
import techreborn.init.template.TechRebornTemplates;
import techreborn.items.DynamicCellItem;
import techreborn.items.tool.MultiblockBuilderItem;
import techreborn.multiblock.IMultiblockStructureMember;
import techreborn.multiblock.MultiblockDefinitionLoader;
import techreborn.multiblock.MultiblockStructureTracker;
import techreborn.packets.Packets;
import techreborn.packets.ServerboundPackets;
import techreborn.utils.PoweredCraftingHandler;
import techreborn.world.WorldGenerator;

public class TechReborn implements ModInitializer {
	public static final String MOD_ID = "techreborn";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		new Configuration(TechRebornConfig.class, "techreborn");
		TRContent.register();

		// Done to force the class to load
		//noinspection ResultOfMethodCallIgnored
		ModRecipes.GRINDER.hashCode();
		TRDataComponentTypes.init();
		TRContent.SCRAP_BOX.asItem();

		Packets.register();;
		ServerboundPackets.init();
		OreDepthSyncHandler.setup();

		if (TechRebornConfig.machineSoundVolume > 0) {
			if (TechRebornConfig.machineSoundVolume > 1) TechRebornConfig.machineSoundVolume = 1F;
			RecipeCrafter.soundHandler = new ModSounds.SoundHandler();
		}
		ModLoot.init();
		WorldGenerator.initWorldGen();
		//Force loads the block entities at the right time
		//noinspection ResultOfMethodCallIgnored
		TRBlockEntities.THERMAL_GEN.toString();
		//noinspection ResultOfMethodCallIgnored
		GuiType.AESU.getIdentifier();
		TRDispenserBehavior.init();
		TRCauldronBehavior.init();
		PoweredCraftingHandler.setup();
		UseBlockHandler.init();
		ApplyArmorToDamageHandler.init();
		FuelRecipes.init();

		// Multiblock Builder tool: right-click a machine controller to inspect
		// missing structure positions, sneak+right-click to auto-build it
		// (10 blocks/tick). The callback returns SUCCESS on both sides so the
		// machine GUI never opens while the tool is held.
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (!player.getStackInHand(hand).isOf(TRContent.MULTIBLOCK_BUILDER)) {
				return ActionResult.PASS;
			}
			if (!(world.getBlockEntity(hitResult.getBlockPos()) instanceof IMultiblockStructureMember machine)) {
				return ActionResult.PASS;
			}
			if (!world.isClient) {
				MultiblockBuilderItem.handleUse(player, world, machine);
			}
			return ActionResult.SUCCESS;
		});
		ServerTickEvents.END_SERVER_TICK.register(MultiblockBuilderItem::tickJobs);

		// Space Elevator binding: right-click the host with an assembler/miner
		// unit item to store the host position on the item (as block entity
		// data, so placing the machine transfers it into its NBT and binds it).
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (!(world.getBlockEntity(hitResult.getBlockPos()) instanceof SpaceElevatorBlockEntity)) {
				return ActionResult.PASS;
			}
			ItemStack stack = player.getStackInHand(hand);
			if (!stack.isOf(TRContent.Machine.SPACE_ELEVATOR_ASSEMBLER.asItem())
					&& !stack.isOf(TRContent.Machine.SPACE_ELEVATOR_MINER.asItem())) {
				return ActionResult.PASS;
			}
			if (!world.isClient) {
				NbtCompound nbt = new NbtCompound();
				nbt.putLong("hostPos", hitResult.getBlockPos().asLong());
				// BLOCK_ENTITY_DATA requires an "id" field to be encoded (the
				// component codec validates it); it is ignored when the block
				// entity NBT is applied on placement.
				nbt.putString("id", stack.isOf(TRContent.Machine.SPACE_ELEVATOR_ASSEMBLER.asItem())
						? "techreborn:space_elevator_assembler" : "techreborn:space_elevator_miner");
				NbtComponent.set(DataComponentTypes.BLOCK_ENTITY_DATA, stack, nbt);
				player.sendMessage(Text.translatable("item.techreborn.space_elevator.bound",
						hitResult.getBlockPos().getX(), hitResult.getBlockPos().getY(), hitResult.getBlockPos().getZ()), true);
			}
			return ActionResult.SUCCESS;
		});


		Torus.genSizeMap(TechRebornConfig.fusionControlComputerMaxCoilSize);

		MultiblockDefinitionLoader.init();

		// Invalidate cached multiblock structure checks as soon as a player
		// breaks a block, so machines stop instantly when their structure is
		// damaged. Non-player block changes (pistons, explosions, water) are
		// covered by the cache TTL in JsonMultiblockMachineBlockEntity.
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) ->
				MultiblockStructureTracker.onBlockChanged(world, pos));

		RedstoneConfiguration.fluidStack = DynamicCellItem.getCellWithFluid(Fluids.LAVA);
		RedstoneConfiguration.powerStack = new ItemStack(TRContent.RED_CELL_BATTERY);

		ComposterBlock.ITEM_TO_LEVEL_INCREASE_CHANCE.put(TRContent.RUBBER_SAPLING.asItem(), 0.3F);
		ComposterBlock.ITEM_TO_LEVEL_INCREASE_CHANCE.put(TRContent.RUBBER_LEAVES.asItem(), 0.3F);
		ComposterBlock.ITEM_TO_LEVEL_INCREASE_CHANCE.put(TRContent.Parts.PLANTBALL.asItem(), 1F);
		ComposterBlock.ITEM_TO_LEVEL_INCREASE_CHANCE.put(TRContent.Parts.COMPRESSED_PLANTBALL.asItem(), 1F);
		ComposterBlock.ITEM_TO_LEVEL_INCREASE_CHANCE.put(TRContent.Dusts.SAW.asItem(), 0.3F);
		ComposterBlock.ITEM_TO_LEVEL_INCREASE_CHANCE.put(TRContent.SmallDusts.SAW.asItem(), 0.1F);

		TechRebornTemplates.init();

		LOGGER.info("TechReborn setup done!");
	}
}
