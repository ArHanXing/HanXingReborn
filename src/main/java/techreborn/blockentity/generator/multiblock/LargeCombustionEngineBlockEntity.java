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

package techreborn.blockentity.generator.multiblock;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.math.BlockPos;
import reborncore.common.fluid.FluidValue;
import reborncore.common.screen.BuiltScreenHandler;
import reborncore.common.screen.BuiltScreenHandlerProvider;
import reborncore.common.screen.builder.ScreenHandlerBuilder;
import techreborn.config.TechRebornConfig;
import techreborn.init.ModRecipes;
import techreborn.init.TRBlockEntities;
import techreborn.init.TRContent;

/**
 * Large Combustion Engine: runs every small diesel generator recipe with 8
 * parallel fuel streams and a 1.25x fuel heat value bonus. Oxygen cells boost
 * the output by 1.5x. All power values come from {@code generators.json}.
 */
public class LargeCombustionEngineBlockEntity extends LargeFluidGeneratorBlockEntity implements BuiltScreenHandlerProvider {

	public LargeCombustionEngineBlockEntity(BlockPos pos, BlockState state) {
		super(TRBlockEntities.LARGE_COMBUSTION_ENGINE, pos, state, ModRecipes.DIESEL_GENERATOR,
				"LargeCombustionEngineBlockEntity", TechRebornConfig.largeCombustionEngineEnergyPerTick);
	}

	@Override
	protected int getConfiguredMaxOutput() {
		return TechRebornConfig.largeCombustionEngineMaxOutput;
	}

	@Override
	protected int getConfiguredMaxEnergy() {
		return TechRebornConfig.largeCombustionEngineMaxEnergy;
	}

	@Override
	public String getMultiblockId() {
		return "large_combustion_engine";
	}

	@Override
	public ItemStack getToolDrop(PlayerEntity entityPlayer) {
		return TRContent.Machine.LARGE_COMBUSTION_ENGINE.getStack();
	}

	@Override
	public BuiltScreenHandler createScreenHandler(int syncID, final PlayerEntity player) {
		return new ScreenHandlerBuilder("largecombustionengine").player(player.getInventory()).inventory().hotbar()
				.addInventory().blockEntity(this).slot(0, 25, 35).outputSlot(1, 25, 55).syncEnergyValue()
				.sync(PacketCodecs.INTEGER, this::getTicksSinceLastChange, this::setTicksSinceLastChange)
				.sync(FluidValue.PACKET_CODEC, this::getTankAmount, this::setTankAmount)
				.sync(tank)
				.addInventory().create(this, syncID);
	}
}
