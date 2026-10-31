/*
 * This file is part of RebornCore, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021 TeamReborn
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

package reborncore.client.gui.config.elements;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.ColorCode;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;
import reborncore.client.gui.GuiBase;
import reborncore.client.gui.GuiSprites;
import reborncore.common.blockentity.FluidConfiguration;
import reborncore.common.network.serverbound.FluidConfigSavePayload;
import reborncore.common.network.serverbound.FluidIoSavePayload;

import java.util.Arrays;

public class FluidConfigPopupElement extends AbstractConfigPopupElement {
	ConfigFluidElement fluidElement;

	public FluidConfigPopupElement(int x, int y, int height, ConfigFluidElement fluidElement) {
		super(
			x, y, height, GuiSprites.SLOT_CONFIG_POPUP, 62, 62,
			Arrays.stream(FluidConfiguration.ExtractConfig.values()).map(Enum::name).toArray(String[]::new)
		);
		this.fluidElement = fluidElement;
	}

	/**
	 * Wider than the default 84: this panel carries the tank selector and the
	 * fluid IO gate underneath the face grid.
	 */
	@Override
	protected int getBackgroundWidth() {
		return 110;
	}

	@Override
	public int getPencilColor(String pencil) {
		return switch (pencil) {
			case "INPUT" -> theme.ioInputColor().rgba();
			case "OUTPUT" -> theme.ioOutputColor().rgba();
			case "ALL" -> theme.ioBothColor().rgba();
			default -> 0x80000000 | theme.warningTextColor().rgba();
		};
	}

	@Override
	protected void cycleConfig(Direction side, GuiBase<?> guiBase) {
		FluidConfiguration.ExtractConfig fluidIO;
		if (pencil != null) {
			fluidIO = switch (pencil) {
				case "INPUT" -> FluidConfiguration.ExtractConfig.INPUT;
				case "OUTPUT" -> FluidConfiguration.ExtractConfig.OUTPUT;
				case "ALL" -> FluidConfiguration.ExtractConfig.ALL;
				default -> FluidConfiguration.ExtractConfig.NONE;
			};
		} else {
			FluidConfiguration.TankConfigHolder holder = holder(guiBase);
			if (holder == null) {
				return;
			}
			FluidConfiguration.FluidConfig config = holder.getSideDetail(side);

			fluidIO = config.getIoConfig().getNext();
		}
		FluidConfiguration.FluidConfig newConfig = new FluidConfiguration.FluidConfig(side, fluidIO);

		ClientPlayNetworking.send(new FluidConfigSavePayload(guiBase.be.getPos(), newConfig, fluidElement.getSelectedTank()));
	}

	public void updateCheckBox(String type, GuiBase<?> guiBase) {
		FluidConfiguration.TankConfigHolder holder = holder(guiBase);
		if (holder == null) {
			return;
		}
		boolean input = holder.autoInput();
		boolean output = holder.autoOutput();
		if (type.equalsIgnoreCase("input")) {
			input = !holder.autoInput();
		}
		if (type.equalsIgnoreCase("output")) {
			output = !holder.autoOutput();
		}

		ClientPlayNetworking.send(new FluidIoSavePayload(guiBase.be.getPos(), input, output, fluidElement.getSelectedTank()));
	}

	/**
	 * @param guiBase {@link GuiBase} the open GUI
	 * @return {@link FluidConfiguration.TankConfigHolder} the tank being edited
	 */
	@Nullable
	private FluidConfiguration.TankConfigHolder holder(GuiBase<?> guiBase) {
		return guiBase.getMachine().fluidConfiguration.getTankDetails(fluidElement.getSelectedTank());
	}

	@Override
	protected void drawSateColor(DrawContext drawContext, GuiBase<?> gui, Direction side, int inx, int iny) {
		iny += 4;
		int sx = inx + getX() + gui.getGuiLeft();
		int sy = iny + getY() + gui.getGuiTop();
		FluidConfiguration.TankConfigHolder holder = holder(gui);
		if (holder == null) {
			return;
		}
		FluidConfiguration.FluidConfig fluidConfig = holder.getSideDetail(side);
		ColorCode color = switch (fluidConfig.getIoConfig()) {
			case NONE -> new ColorCode(0);
			case INPUT -> theme.ioInputColor();
			case OUTPUT -> theme.ioOutputColor();
			case ALL -> theme.ioBothColor();
		};
		drawContext.fill(sx, sy, sx + 18, sy + 18, color.rgba());
	}
}
