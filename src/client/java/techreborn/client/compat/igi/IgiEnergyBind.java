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

package techreborn.client.compat.igi;

import df11zomgraves.ingameinfo.api.InGameInfoAPI;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import techreborn.TechReborn;

/**
 * Registers the cable-network tags with InGame Info XML.
 * <p>
 * InGame Info XML is a soft dependency: it is compiled against but not required
 * at runtime, so the whole registration is behind an {@code isModLoaded} check.
 * Registration goes through {@link InGameInfoAPI} rather than the raw registry
 * because the two mods' client initialisers have no guaranteed order.
 */
@Environment(EnvType.CLIENT)
public final class IgiEnergyBind {

	private IgiEnergyBind() {
	}

	public static void init() {
		if (!FabricLoader.getInstance().isModLoaded("ingameinfoxml")) {
			return;
		}

		EnergyBindClientState.register();

		register("techreborn_energy_in", new EnergyBindTags.AverageInput());
		register("techreborn_energy_out", new EnergyBindTags.AverageOutput());
		register("techreborn_energy_buffer", new EnergyBindTags.Buffer());
		register("techreborn_energy_percent", new EnergyBindTags.BufferPercent());
		register("techreborn_energy_eta", new EnergyBindTags.TimeToEdge());

		TechReborn.LOGGER.info("Registered InGame Info XML cable network tags");
	}

	private static void register(String name, df11zomgraves.ingameinfo.tag.Tag tag) {
		// A name clash would be rejected with only an error log, so check first and
		// keep our own name instead of silently losing the tag.
		if (InGameInfoAPI.isTagRegistered(name)) {
			TechReborn.LOGGER.warn("InGame Info XML tag '{}' is already taken, skipping", name);
			return;
		}
		tag.setName(name);
		InGameInfoAPI.registerTag(tag);
	}
}
