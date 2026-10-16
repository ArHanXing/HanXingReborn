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

import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import reborncore.common.powerSystem.PowerSystem;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import techreborn.blockentity.cable.CableBlockEntity;
import techreborn.blockentity.cable.CableNetwork;
import techreborn.blockentity.cable.CableNetworkManager;

/**
 * Shows the electrical network a cable belongs to:
 * <ul>
 *     <li>How many cables the network is made of</li>
 *     <li>The energy currently buffered across it, and its total capacity</li>
 *     <li>What the network pulled in from generators last tick</li>
 *     <li>What it delivered to consumers last tick</li>
 * </ul>
 * The numbers come from {@link CableTickManager}, which already computes them
 * every tick while it pools and redistributes the network's energy; the
 * union-find in {@link CableNetworkManager} is what turns "which network is
 * this cable on" into a constant-time lookup.
 * <p>
 * Everything is gathered server side, so the client never has to be able to see
 * the rest of the network.
 */
public enum CableNetworkProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
	INSTANCE;

	@Override
	public void appendServerData(NbtCompound data, BlockAccessor accessor) {
		if (!(accessor.getBlockEntity() instanceof CableBlockEntity cable)) {
			return;
		}
		CableNetwork network = CableNetworkManager.networkOf(cable);
		if (network == null || network.size() == 0) {
			return;
		}
		data.putInt("cable_size", network.size());
		data.putLong("cable_buffer", network.getBuffer());
		data.putLong("cable_capacity", network.getCapacity());
		data.putLong("cable_input", network.getInput());
		data.putLong("cable_output", network.getOutput());
	}

	@Override
	public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
		if (!(accessor.getBlockEntity() instanceof CableBlockEntity)) {
			return;
		}
		NbtCompound data = accessor.getServerData();
		if (!data.contains("cable_size")) {
			return;
		}

		tooltip.add(Text.translatable("jade.techreborn.cable_network_size",
				Text.literal(String.valueOf(data.getInt("cable_size"))).formatted(Formatting.GOLD)));
		tooltip.add(Text.translatable("jade.techreborn.cable_network_buffer",
				Text.literal(PowerSystem.getLocalizedPower(data.getLong("cable_buffer"))).formatted(Formatting.GOLD),
				Text.literal(PowerSystem.getLocalizedPower(data.getLong("cable_capacity"))).formatted(Formatting.GOLD)));
		tooltip.add(Text.translatable("jade.techreborn.cable_network_input",
				Text.literal(PowerSystem.getLocalizedPower(data.getLong("cable_input"))).formatted(Formatting.GOLD)));
		tooltip.add(Text.translatable("jade.techreborn.cable_network_output",
				Text.literal(PowerSystem.getLocalizedPower(data.getLong("cable_output"))).formatted(Formatting.GOLD)));
	}

	@Override
	public Identifier getUid() {
		return TechRebornJadePlugin.CABLE_NETWORK;
	}
}
