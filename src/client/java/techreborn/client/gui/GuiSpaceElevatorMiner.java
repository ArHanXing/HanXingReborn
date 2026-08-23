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

package techreborn.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import techreborn.blockentity.machine.multiblock.SpaceElevatorMinerBlockEntity;

/**
 * GUI of the Space Elevator Miner Unit: the Distillation Tower layout plus a
 * line showing the bound host coordinates (or a hint when unbound).
 */
public class GuiSpaceElevatorMiner extends GuiDistillationTower<SpaceElevatorMinerBlockEntity> {

	private final SpaceElevatorMinerBlockEntity blockEntity;

	public GuiSpaceElevatorMiner(int syncID, final PlayerEntity player, SpaceElevatorMinerBlockEntity blockEntity) {
		super(syncID, player, blockEntity);
		this.blockEntity = blockEntity;
	}

	@Override
	protected void drawForeground(DrawContext drawContext, final int mouseX, final int mouseY) {
		super.drawForeground(drawContext, mouseX, mouseY);
		SpaceElevatorGuiUtils.drawBoundInfo(this, drawContext, blockEntity.getHostPos(), 8, 88, Layer.FOREGROUND);
	}
}
