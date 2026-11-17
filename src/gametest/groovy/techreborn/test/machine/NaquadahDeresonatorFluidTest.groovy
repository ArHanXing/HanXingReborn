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
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction
import net.minecraft.fluid.Fluids
import net.minecraft.item.Items
import net.minecraft.nbt.NbtCompound
import net.minecraft.test.GameTest
import reborncore.common.blockentity.MultiTankStorage
import reborncore.common.crafting.SizedIngredient
import reborncore.common.fluid.FluidValue
import reborncore.common.fluid.container.FluidInstance
import techreborn.blockentity.machine.multiblock.NaquadahDeresonatorBlockEntity
import techreborn.init.ModFluids
import techreborn.init.ModRecipes
import techreborn.recipe.recipes.NaquadahDeresonatorRecipe
import techreborn.test.TRGameTest
import techreborn.test.TRTestContext

import java.util.Optional

/**
 * Covers the Naquadah Deresonator's fluid product.
 * <p>
 * The recipe names only the output fluid id; the amount always mirrors what the
 * run drew from the input tank. These tests therefore drive the accounting
 * directly instead of assembling the multiblock, which is covered elsewhere.
 */
class NaquadahDeresonatorFluidTest extends TRGameTest {

	private static NaquadahDeresonatorRecipe makeRecipe(net.minecraft.fluid.Fluid output) {
		new NaquadahDeresonatorRecipe(ModRecipes.NAQUADAH_DERESONATOR,
			List.of(new SizedIngredient(1, net.minecraft.recipe.Ingredient.ofItems(Items.IRON_INGOT))),
			List.of(Items.IRON_INGOT.getDefaultStack()), 1, 1, ModFluids.MERCURY.getFluid(),
			Optional.of(output.getRegistryEntry()))
	}

	@GameTest(templateName = "fabric-gametest-api-v1:empty", tickLimit = 20)
	def testProductMirrorsInputAmount(TRTestContext context) {
		context.machine(techreborn.init.TRContent.Machine.NAQUADAH_DERESONATOR) {
			NaquadahDeresonatorBlockEntity be = blockEntity as NaquadahDeresonatorBlockEntity
			NaquadahDeresonatorRecipe recipe = makeRecipe(Fluids.WATER)

			if (!recipe.outputFluid().isPresent() || recipe.outputFluid().get() != Fluids.WATER) {
				throwGameTestException("Recipe should expose its fluid product")
			}

			// Three buckets in, three buckets out - the recipe carries no amount.
			be.setRunFluidRaw(FluidValue.BUCKET.multiply(3).getRawValue())
			if (!be.depositRunFluidProduct(recipe.outputFluid().get())) {
				throwGameTestException("Product should fit an empty tank")
			}
			if (be.outputTank.getFluid() != Fluids.WATER
				|| be.outputTank.getAmount() != FluidValue.BUCKET.multiply(3).getRawValue()) {
				throwGameTestException("Product amount must equal the fluid the run consumed")
			}

			// A second run accumulates instead of replacing.
			be.setRunFluidRaw(FluidValue.BUCKET.getRawValue())
			if (!be.depositRunFluidProduct(recipe.outputFluid().get())) {
				throwGameTestException("Product should accumulate")
			}
			if (be.outputTank.getAmount() != FluidValue.BUCKET.multiply(4).getRawValue()) {
				throwGameTestException("Product should add up across runs")
			}
			addInstantFinalTask {}
		}
	}

	@GameTest(templateName = "fabric-gametest-api-v1:empty", tickLimit = 20)
	def testProductRejectsWrongFluidAndFullTank(TRTestContext context) {
		context.machine(techreborn.init.TRContent.Machine.NAQUADAH_DERESONATOR) {
			NaquadahDeresonatorBlockEntity be = blockEntity as NaquadahDeresonatorBlockEntity
			NaquadahDeresonatorRecipe recipe = makeRecipe(Fluids.WATER)

			// A tank holding another fluid must not be topped up with this product.
			be.outputTank.setFluidInstance(new FluidInstance(Fluids.LAVA, FluidValue.BUCKET))
			be.setRunFluidRaw(FluidValue.BUCKET.getRawValue())
			long lavaBefore = be.outputTank.getAmount()
			if (be.depositRunFluidProduct(recipe.outputFluid().get())) {
				throwGameTestException("Product must not mix into a tank holding another fluid")
			}
			if (be.outputTank.getFluid() != Fluids.LAVA || be.outputTank.getAmount() != lavaBefore) {
				throwGameTestException("Rejected product must leave the tank untouched")
			}

			// A full tank has no room and must report failure, not void the fluid.
			be.outputTank.setFluidInstance(new FluidInstance(Fluids.WATER,
				NaquadahDeresonatorBlockEntity.OUTPUT_TANK_CAPACITY))
			be.setRunFluidRaw(FluidValue.BUCKET.getRawValue())
			if (be.depositRunFluidProduct(recipe.outputFluid().get())) {
				throwGameTestException("A full tank must reject the product")
			}
			if (be.hasRoomForFluidOutput(true)) {
				throwGameTestException("A full tank must report no room for a fluid product")
			}
			if (!be.hasRoomForFluidOutput(false)) {
				throwGameTestException("Recipes without a product must not care about the output tank")
			}
			addInstantFinalTask {}
		}
	}

	@GameTest(templateName = "fabric-gametest-api-v1:empty", tickLimit = 20)
	def testOutputTankRolesAndPersistence(TRTestContext context) {
		context.machine(techreborn.init.TRContent.Machine.NAQUADAH_DERESONATOR) {
			NaquadahDeresonatorBlockEntity be = blockEntity as NaquadahDeresonatorBlockEntity

			if (be.getTank(0) != be.tank || be.getTank(1) != be.outputTank || be.getTankCount() != 2) {
				throwGameTestException("Expected stable input/output tank indexes")
			}
			if (be.canInsertFluid(1) || !be.canExtractFluid(1)) {
				throwGameTestException("Product tank must be drain-only")
			}

			// The aggregate storage must never route an insert into the product tank.
			be.tank.setFluidInstance(new FluidInstance(ModFluids.MERCURY.getFluid(), NaquadahDeresonatorBlockEntity.TANK_CAPACITY))
			be.outputTank.setFluidInstance(new FluidInstance(Fluids.WATER, FluidValue.BUCKET))
			MultiTankStorage storage = new MultiTankStorage(be)
			long productBefore = be.outputTank.getAmount()
			try (Transaction tx = Transaction.openOuter()) {
				long inserted = storage.insert(FluidVariant.of(Fluids.WATER), 1, tx)
				if (inserted != 0 || be.outputTank.getAmount() != productBefore) {
					throwGameTestException("Aggregate insertion must not reach the product tank")
				}
			}

			be.setRunFluidRaw(FluidValue.BUCKET.multiply(2).getRawValue())
			NbtCompound nbt = new NbtCompound()
			be.writeNbt(nbt, world.getRegistryManager())
			NaquadahDeresonatorBlockEntity restored =
				new NaquadahDeresonatorBlockEntity(be.pos, be.cachedState)
			restored.readNbt(nbt, world.getRegistryManager())
			if (restored.getRunFluidRaw() != FluidValue.BUCKET.multiply(2).getRawValue()) {
				throwGameTestException("Run fluid must survive NBT")
			}
			if (!restored.outputTank.getResource().equals(FluidVariant.of(Fluids.WATER))
				|| restored.outputTank.getAmount() != FluidValue.BUCKET.getRawValue()) {
				throwGameTestException("Product tank must survive NBT")
			}
			addInstantFinalTask {}
		}
	}
}
