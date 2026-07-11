package techreborn.client.compat.emi;

import net.fabricmc.fabric.api.transfer.v1.client.fluid.FluidVariantRendering;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;

import net.minecraft.client.util.math.MatrixStack;

import techreborn.client.compat.emi.core.UIUtils;

public class FabricUIUtils {
	public static void renderFluid(MatrixStack matrices, FluidVariant fluid, int x, int areaY,
	                               float areaHeight, float fluidHeight, float fluidWidth) {
		UIUtils.renderFluid(matrices, FluidVariantRendering.getSprites(fluid), FluidVariantRendering.getColor(fluid), x,
			areaY, areaHeight, fluidHeight, fluidWidth);
	}
}
