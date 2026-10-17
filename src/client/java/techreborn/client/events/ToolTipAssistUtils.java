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

package techreborn.client.events;

import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import techreborn.blockentity.machine.multiblock.VoidDrillBlockEntity;
import techreborn.config.TechRebornConfig;
import techreborn.init.TRContent;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


public class ToolTipAssistUtils {

	// Colour constants
	private static final Formatting infoColour = Formatting.GOLD;
	private static final Formatting statColour = Formatting.YELLOW;

	private static final Formatting posColour = Formatting.GREEN;
	private static final Formatting negColour = Formatting.RED;

	/** Machines expose four upgrade slots; the preview models filling all of them. */
	public static final int MAX_UPGRADE_SLOTS = 4;

	/**
	 * Builds the effect lines of an upgrade.
	 * <p>
	 * The count is a number of <b>slots</b>, not of items. A machine applies an
	 * upgrade once per slot and completely ignores the stack size, so four
	 * overclockers in one slot are worth exactly as much as a single one. The
	 * preview therefore reports one upgrade normally and four (the slot limit)
	 * while shift is held, which is the most a machine can ever benefit from; it
	 * deliberately does not grow with the stack.
	 *
	 * @param upgradeType {@link TRContent.Upgrades} the upgrade
	 * @param shiftHeld   {@code boolean} whether to preview every upgrade slot
	 * @return {@link List} the tooltip lines
	 */
	public static List<Text> getUpgradeStats(TRContent.Upgrades upgradeType, boolean shiftHeld) {
		List<Text> tips = new ArrayList<>();
		int slots = shiftHeld ? MAX_UPGRADE_SLOTS : 1;

		switch (upgradeType) {
			case OVERCLOCKER -> {
				tips.add(statLine("techreborn.tooltip.upgrade.speed_increase",
						percent(1 - Math.pow(TechRebornConfig.overclockerSpeed, slots)), true));
				tips.add(statLine("techreborn.tooltip.upgrade.energy_increase",
						times(Math.pow(TechRebornConfig.overclockerPower + 1, slots)), false));
				if (shiftHeld) {
					appendOverclockerMachines(tips, slots);
				}
			}
			case TRANSFORMER -> {
			}
			case ENERGY_STORAGE -> tips.add(statLine("techreborn.tooltip.upgrade.storage_increase",
					times(Math.pow(2, slots)), true));
			case SUPERCONDUCTOR -> tips.add(statLine("techreborn.tooltip.upgrade.flow_increase",
					FORMAT.format(Math.pow(2, TechRebornConfig.superConductorCount + 2) * 100 * slots) + "%", true));
		}

		if (!shiftHeld) {
			tips.add(Text.translatable("techreborn.tooltip.upgrade.hold_shift",
					Text.literal(String.valueOf(MAX_UPGRADE_SLOTS)).formatted(statColour))
					.formatted(Formatting.DARK_GRAY));
		}

		return tips;
	}

	/**
	 * Adds the machines whose overclocker does not follow the generic curve, so
	 * the player can see what it is actually worth there.
	 * <p>
	 * The Digital Miner uses its own multipliers (0.5x break time, 2x draw per
	 * overclocker) instead of the generic 0.5 / 1.25, and the Void Drill gets a
	 * depletion discount on top of the usual speed and power effects.
	 *
	 * @param tips  {@link List} the tooltip lines to append to
	 * @param slots {@code int} how many overclockers are being previewed
	 */
	private static void appendOverclockerMachines(List<Text> tips, int slots) {
		tips.add(Text.translatable("techreborn.tooltip.upgrade.overclocker.slots",
						Text.literal(String.valueOf(slots)).formatted(statColour))
				.formatted(Formatting.DARK_GRAY));

		tips.add(Text.translatable("techreborn.tooltip.upgrade.overclocker.digital_miner",
						times(Math.pow(TechRebornConfig.digitalMinerSpeedMultiplierPerOverclocker, slots)),
						times(Math.pow(TechRebornConfig.digitalMinerPowerMultiplierPerOverclocker, slots)))
				.formatted(Formatting.AQUA));

		double depletion = Math.max(VoidDrillBlockEntity.MIN_DEPLETION,
				VoidDrillBlockEntity.BASE_DEPLETION - VoidDrillBlockEntity.DEPLETION_PER_OVERCLOCKER * slots);
		tips.add(Text.translatable("techreborn.tooltip.upgrade.overclocker.void_drill",
						percent(depletion), percent(VoidDrillBlockEntity.MIN_DEPLETION))
				.formatted(Formatting.AQUA));
	}

	/**
	 * Appends the machine's description lines ("techreborn.message.info.*")
	 * in gold. Descriptions are always visible; no shift required.
	 */
	public static void addInfo(String inKey, List<Text> list) {
		addInfo(inKey, list, true);
	}

	public static void addInfo(String inKey, List<Text> list, boolean hidden) {
		String key = ("techreborn.message.info." + inKey);

		if (I18n.hasTranslation(key)) {
			String info = I18n.translate(key);
			List<MutableText> infoLines = java.util.Arrays.stream(info.split("\\r?\\n"))
					.map(ToolTipAssistUtils::highlight)
					.toList();
			list.addAll(1, infoLines);
		}
	}

	/**
	 * Applies inline colour markers in a translated description string:
	 * {@code &e} gold (default description colour), {@code &b} aqua for
	 * cautions, {@code &a} green for machine specialties. Unmarked text
	 * keeps the description gold. Also converts the legacy {@code §} codes
	 * of vanilla formatting so lang files can use either form.
	 */
	public static MutableText highlight(String line) {
		MutableText result = Text.literal("");
		Formatting current = infoColour;
		int i = 0;
		while (i < line.length()) {
			char c = line.charAt(i);
			boolean isMarker = (c == '&' || c == '§') && i + 1 < line.length();
			if (isMarker) {
				Formatting parsed = Formatting.byCode(line.charAt(i + 1));
				if (parsed != null) {
					current = parsed;
					i += 2;
					continue;
				}
			}
			int next = i;
			while (next < line.length()) {
				char nc = line.charAt(next);
				if ((nc == '&' || nc == '§') && next + 1 < line.length()
						&& Formatting.byCode(line.charAt(next + 1)) != null) {
					break;
				}
				next++;
			}
			result.append(Text.literal(line.substring(i, next)).formatted(current));
			i = next;
		}
		return result;
	}

	/**
	 * Appends a gray "label: value" line; value strings are gold.
	 */
	public static void addStat(List<Text> list, String labelKey, String value) {
		list.add(Text.translatable(labelKey).formatted(Formatting.GRAY)
				.append(": ").append(Text.literal(value).formatted(statColour)));
	}

	private static final NumberFormat FORMAT =
			new DecimalFormat("##.##", new DecimalFormatSymbols(Locale.US));

	private static String percent(double ratio) {
		return FORMAT.format(ratio * 100) + "%";
	}

	private static String times(double multiplier) {
		return FORMAT.format(multiplier) + "x";
	}

	/**
	 * A "label: value" line built from real text components. The previous
	 * implementation concatenated {@link Formatting} constants into a single
	 * literal, which embeds legacy section-sign codes that modern tooltips no
	 * longer interpret.
	 *
	 * @param labelKey   {@link String} translation key of the label
	 * @param value      {@link String} the formatted value
	 * @param isPositive {@code boolean} green when positive, red otherwise
	 * @return {@link Text} the line
	 */
	private static Text statLine(String labelKey, String value, boolean isPositive) {
		return Text.translatable(labelKey).formatted(infoColour)
				.append(Text.literal(": ").formatted(infoColour))
				.append(Text.literal(value).formatted(isPositive ? posColour : negColour));
	}
}
