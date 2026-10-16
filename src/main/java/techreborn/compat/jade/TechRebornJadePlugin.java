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

import net.minecraft.util.Identifier;
import reborncore.common.blockentity.MachineBaseBlockEntity;
import reborncore.common.blocks.BlockMachineBase;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import techreborn.blockentity.cable.CableBlockEntity;
import techreborn.blocks.cable.CableBlock;

/**
 * Jade (WAILA/Hwyla successor) plugin that shows the currently running recipe
 * on TechReborn machines: inputs, outputs, energy usage, parallel count and
 * progress, plus the electrical network a cable belongs to. Registered through
 * the {@code "jade"} entrypoint in fabric.mod.json.
 */
@WailaPlugin
public class TechRebornJadePlugin implements IWailaPlugin {

	public static final Identifier MACHINE_RECIPE = Identifier.of("techreborn", "machine_recipe");
	public static final Identifier CABLE_NETWORK = Identifier.of("techreborn", "cable_network");

	@Override
	public void register(IWailaCommonRegistration registration) {
		// Server side: sync recipe state every 250ms
		registration.registerBlockDataProvider(MachineRecipeProvider.INSTANCE, MachineBaseBlockEntity.class);
		// Server side: cables report their network's aggregate values, which the
		// client has no way of working out on its own.
		registration.registerBlockDataProvider(CableNetworkProvider.INSTANCE, CableBlockEntity.class);
	}

	@Override
	public void registerClient(IWailaClientRegistration registration) {
		// Client side: render the recipe lines
		registration.registerBlockComponent(MachineRecipeProvider.INSTANCE, BlockMachineBase.class);
		// Client side: render the network lines
		registration.registerBlockComponent(CableNetworkProvider.INSTANCE, CableBlock.class);
	}
}
