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

package techreborn.test.machine

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction
import net.minecraft.fluid.Fluids
import net.minecraft.item.Items
import net.minecraft.nbt.NbtCompound
import net.minecraft.test.GameTest
import reborncore.common.blockentity.MultiTankStorage
import reborncore.common.crafting.SizedIngredient
import reborncore.common.fluid.FluidValue
import reborncore.common.fluid.container.FluidInstance
import reborncore.common.util.Tank
import techreborn.blockentity.machine.multiblock.IndustrialGrinderBlockEntity
import techreborn.init.ModRecipes
import techreborn.recipe.recipes.IndustrialGrinderRecipe
import techreborn.test.TRGameTest
import techreborn.test.TRTestContext

class IndustrialGrinderFluidTest extends TRGameTest {
	private static IndustrialGrinderRecipe makeRecipe(FluidInstance input, FluidInstance output) {
		new IndustrialGrinderRecipe(ModRecipes.INDUSTRIAL_GRINDER,
			List.of(new SizedIngredient(1, net.minecraft.recipe.Ingredient.ofItems(Items.IRON_INGOT))),
			List.of(Items.IRON_NUGGET.getDefaultStack()), 1, 1, input, output)
	}

	@GameTest(templateName = "fabric-gametest-api-v1:empty", tickLimit = 20)
	def testOutputTankRolesAndNbt(TRTestContext context) {
		context.machine(techreborn.init.TRContent.Machine.INDUSTRIAL_GRINDER) {
			IndustrialGrinderBlockEntity be = blockEntity as IndustrialGrinderBlockEntity
			if (be.getTank(0) != be.tank || be.getTank(1) != be.outputTank || be.getTankCount() != 2) {
				throwGameTestException("Expected stable input/output tank indexes")
			}
			be.tank.setFluidInstance(new FluidInstance(Fluids.LAVA, FluidValue.BUCKET.multiply(256)))
			be.outputTank.setFluidInstance(new FluidInstance(Fluids.WATER, FluidValue.BUCKET))
			if (be.canInsertFluid(1) || !be.canExtractFluid(1)) throwGameTestException("Output tank external role incorrect")
			MultiTankStorage storage = new MultiTankStorage(be)
			long outputBefore = be.outputTank.getAmount()
			try (Transaction tx = Transaction.openOuter()) {
				long inserted = storage.insert(FluidVariant.of(Fluids.WATER), 1, tx)
				if (inserted != 0 || be.outputTank.getAmount() != outputBefore) {
					throwGameTestException("Aggregate insertion must not route into output tank")
				}
			}
			StorageView<FluidVariant> outputView = storage.iterator().find {
				!it.isResourceBlank() && it.getResource().equals(FluidVariant.of(Fluids.WATER))
			} as StorageView<FluidVariant>
			if (outputView == null || outputView.getAmount() != outputBefore) {
				throwGameTestException("Output tank view should be exposed for extraction")
			}
			try (Transaction tx = Transaction.openOuter()) {
				long extracted = outputView.extract(FluidVariant.of(Fluids.WATER), 1, tx)
				if (extracted != 1) throwGameTestException("Output tank view should be drainable")
				tx.commit()
			}
			if (be.outputTank.getAmount() != outputBefore - 1) {
				throwGameTestException("Direct view extraction should drain the output tank")
			}
			be.outputTank.setFluidInstance(new FluidInstance(Fluids.WATER, FluidValue.BUCKET))

			NbtCompound nbt = new NbtCompound()
			be.writeNbt(nbt, world.getRegistryManager())
			IndustrialGrinderBlockEntity restored = new IndustrialGrinderBlockEntity(be.pos, be.cachedState)
			restored.readNbt(nbt, world.getRegistryManager())
			if (restored.tank.getFluid() != Fluids.LAVA || restored.tank.getAmount() != FluidValue.BUCKET.multiply(256).getRawValue()
				|| !restored.outputTank.getResource().equals(FluidVariant.of(Fluids.WATER))
				|| restored.outputTank.getAmount() != FluidValue.BUCKET.getRawValue()) {
				throwGameTestException("Both tank states should survive NBT round trip")
			}
			addInstantFinalTask {}
		}
	}

	@GameTest(templateName = "fabric-gametest-api-v1:empty", tickLimit = 20)
	def testRecipeFluidOutputAtomicityAndParallel(TRTestContext context) {
		context.machine(techreborn.init.TRContent.Machine.INDUSTRIAL_GRINDER) {
			IndustrialGrinderBlockEntity be = blockEntity as IndustrialGrinderBlockEntity
			def input = new FluidInstance(Fluids.WATER, FluidValue.BUCKET)
			def output = new FluidInstance(Fluids.LAVA, FluidValue.BUCKET)
			IndustrialGrinderRecipe recipe = makeRecipe(input, output)

			be.tank.setFluidInstance(new FluidInstance(Fluids.WATER, FluidValue.BUCKET.multiply(2)))
			be.outputTank.setFluidInstance(new FluidInstance(Fluids.LAVA, FluidValue.BUCKET.multiply(254)))
			if (recipe.getParallelOutputLimit(be) != 2) throwGameTestException("Parallel output limit should fit two runs")
			if (!recipe.onCraft(be, 2)) throwGameTestException("Two fluid-output runs should fit")
			if (be.tank.getAmount() != 0 || be.outputTank.getAmount() != FluidValue.BUCKET.multiply(256).getRawValue()) {
				throwGameTestException("Parallel craft must consume and produce exactly two buckets")
			}

			be.tank.setFluidInstance(new FluidInstance(Fluids.WATER, FluidValue.BUCKET))
			be.outputTank.setFluidInstance(new FluidInstance(Fluids.LAVA, FluidValue.BUCKET.multiply(255)))
			long inputBefore = be.tank.getAmount()
			long outputBefore = be.outputTank.getAmount()
			if (recipe.onCraft(be, 2) || be.tank.getAmount() != inputBefore || be.outputTank.getAmount() != outputBefore) {
				throwGameTestException("Insufficient input/output space must not mutate either tank")
			}

			be.tank.setFluidInstance(new FluidInstance(Fluids.WATER, FluidValue.BUCKET))
			be.outputTank.setFluidInstance(new FluidInstance(Fluids.WATER, FluidValue.BUCKET))
			inputBefore = be.tank.getAmount()
			outputBefore = be.outputTank.getAmount()
			if (recipe.onCraft(be, 1) || be.tank.getAmount() != inputBefore || be.outputTank.getAmount() != outputBefore) {
				throwGameTestException("Wrong output fluid must reject atomically")
			}

			be.outputTank.setFluidInstance(FluidInstance.EMPTY)
			be.tank.setFluidInstance(new FluidInstance(Fluids.WATER, FluidValue.BUCKET))
			IndustrialGrinderRecipe legacy = makeRecipe(FluidInstance.EMPTY, FluidInstance.EMPTY)
			if (!legacy.fluidOutput().isEmpty() || legacy.getParallelOutputLimit(be) < 1) {
				throwGameTestException("Legacy recipes without output fluid must remain unrestricted")
			}
			addInstantFinalTask {}
		}
	}
}
