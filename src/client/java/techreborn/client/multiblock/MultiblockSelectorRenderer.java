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

package techreborn.client.multiblock;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import techreborn.init.TRContent;

import java.util.List;

/**
 * Renders a red outline around every block selected with the multiblock
 * selector tool while the tool is held in the main hand.
 * The outline is drawn as several nested boxes to make it appear bolder.
 */
public class MultiblockSelectorRenderer {

	private static final int OUTLINE_LAYERS = 3;
	private static final double OUTLINE_GROWTH = 0.04;

	public static void init() {
		WorldRenderEvents.BLOCK_OUTLINE.register((context, outlineContext) -> {
			ClientPlayerEntity player = MinecraftClient.getInstance().player;
			if (player == null || !player.getMainHandStack().isOf(TRContent.MULTIBLOCK_SELECTOR)) {
				return false; // Use the default outline
			}

			List<BlockPos> positions = MultiblockSelector.INSTANCE.getSelectedPositions();
			if (!positions.isEmpty()) {
				double camX = outlineContext.cameraX();
				double camY = outlineContext.cameraY();
				double camZ = outlineContext.cameraZ();
				VertexConsumer lineConsumer = context.consumers().getBuffer(RenderLayer.getLines());
				for (BlockPos pos : positions) {
					double px = pos.getX() - camX;
					double py = pos.getY() - camY;
					double pz = pos.getZ() - camZ;

					// Bold red border: several nested line boxes
					for (int i = 0; i < OUTLINE_LAYERS; i++) {
						double e = i * OUTLINE_GROWTH;
						WorldRenderer.drawBox(context.matrixStack(), lineConsumer,
								new Box(px - e, py - e, pz - e, px + 1.0 + e, py + 1.0 + e, pz + 1.0 + e),
								1.0F, 0.3F, 0.3F, 1.0F);
					}
				}
			}
			return true;
		});
	}
}
