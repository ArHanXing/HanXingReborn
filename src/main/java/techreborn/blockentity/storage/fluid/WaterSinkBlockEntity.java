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

package techreborn.blockentity.storage.fluid;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import reborncore.api.IToolDrop;
import reborncore.api.blockentity.InventoryProvider;
import reborncore.common.blockentity.MachineBaseBlockEntity;
import reborncore.common.fluid.FluidUtils;
import reborncore.common.fluid.FluidValue;
import reborncore.common.fluid.container.FluidInstance;
import reborncore.common.screen.BuiltScreenHandler;
import reborncore.common.screen.BuiltScreenHandlerProvider;
import reborncore.common.screen.builder.ScreenHandlerBuilder;
import reborncore.common.util.RebornInventory;
import reborncore.common.util.Tank;
import techreborn.init.TRBlockEntities;
import techreborn.init.TRContent;

/**
 * An infinite water source in the shape of a sink.
 * <p>
 * Essentially the tank unit's machinery -- a {@link Tank}, a cell slot and the
 * fluid IO config -- with two differences: the tank is enormous ({@value
 * #CAPACITY_MB} mB) and it tops itself up with water every
 * {@value #REFILL_INTERVAL_TICKS} ticks, so it never runs dry.
 * <p>
 * Deliberately not a subclass of {@code TankUnitBaseBlockEntity}: that class
 * carries a {@code TRContent.TankUnit} tier and dereferences it in {@code
 * writeNbt} and {@code fluidTransferAmount}, so a tank without a tier would
 * crash on save. This class owns its tank outright instead.
 */
public class WaterSinkBlockEntity extends MachineBaseBlockEntity
		implements BuiltScreenHandlerProvider, InventoryProvider, IToolDrop {

	/** Capacity in millibuckets; a little under a billion buckets. */
	public static final long CAPACITY_MB = 998_244_353_000L;

	/** How often the sink refills itself. */
	public static final int REFILL_INTERVAL_TICKS = 20;

	/** Cell input / output slots. */
	private static final int CELL_INPUT_SLOT = 0;
	private static final int CELL_OUTPUT_SLOT = 1;

	public final Tank tank;
	public final RebornInventory<WaterSinkBlockEntity> inventory =
		new RebornInventory<>(2, "WaterSinkInventory", 64, this);

	private int refillTicks = 0;

	public WaterSinkBlockEntity(BlockPos pos, BlockState state) {
		super(TRBlockEntities.WATER_SINK, pos, state);
		this.tank = new Tank("WaterSink", FluidValue.fromMillibuckets(CAPACITY_MB));
		this.tank.setFluidInstance(new FluidInstance(Fluids.WATER, tank.getFluidValueCapacity()));
	}

	@Override
	@Nullable
	public Tank getTank() {
		return tank;
	}

	/**
	 * The sink holds water by design, so it takes the whole capacity as a single
	 * transfer step rather than dribbling it like a tiered tank unit.
	 */
	@Override
	public FluidValue fluidTransferAmount() {
		return FluidValue.BUCKET;
	}

	@Override
	public RebornInventory<?> getInventory() {
		return inventory;
	}

	@Override
	public void tick(World world, BlockPos pos, BlockState state, MachineBaseBlockEntity blockEntity) {
		super.tick(world, pos, state, blockEntity);
		if (world == null || world.isClient) {
			return;
		}

		// Cell handling, same shape as the tank unit: a full cell is emptied into
		// the tank, an empty one is filled from it.
		if (FluidUtils.isContainer(inventory.getStack(CELL_INPUT_SLOT))) {
			boolean didSomething = FluidUtils.drainContainers(tank, inventory, CELL_INPUT_SLOT, CELL_OUTPUT_SLOT);
			if (!didSomething) {
				didSomething = FluidUtils.fillContainers(tank, inventory, CELL_INPUT_SLOT, CELL_OUTPUT_SLOT);
			}
			if (didSomething && inventory.getStack(CELL_OUTPUT_SLOT).isEmpty()
					&& !inventory.getStack(CELL_INPUT_SLOT).isEmpty()
					&& inventory.getStack(CELL_INPUT_SLOT).getCount() == 1) {
				inventory.setStack(CELL_OUTPUT_SLOT, inventory.getStack(CELL_INPUT_SLOT));
				inventory.setStack(CELL_INPUT_SLOT, ItemStack.EMPTY);
			}
		}

		if (++refillTicks >= REFILL_INTERVAL_TICKS) {
			refillTicks = 0;
			refill();
		}
	}

	/**
	 * Tops the tank back up with water. Only the free space is filled, and only
	 * when the tank still holds water: a tank someone deliberately filled with
	 * another fluid is left alone rather than silently emptied.
	 */
	private void refill() {
		if (!tank.getFluid().matchesType(Fluids.WATER)) {
			return;
		}
		FluidValue capacity = tank.getFluidValueCapacity();
		if (tank.getFluidAmount().equalOrMoreThan(capacity)) {
			return;
		}
		tank.setFluidInstance(new FluidInstance(Fluids.WATER, capacity));
		markDirty();
		syncWithAll();
	}

	@Override
	public void readNbt(NbtCompound tagCompound, RegistryWrapper.WrapperLookup registryLookup) {
		super.readNbt(tagCompound, registryLookup);
		tank.read(tagCompound, registryLookup);
		refillTicks = tagCompound.getInt("refillTicks");
	}

	@Override
	public void writeNbt(NbtCompound tagCompound, RegistryWrapper.WrapperLookup registryLookup) {
		super.writeNbt(tagCompound, registryLookup);
		tank.write(tagCompound, registryLookup);
		tagCompound.putInt("refillTicks", refillTicks);
	}

	@Override
	public BuiltScreenHandler createScreenHandler(int syncID, final PlayerEntity player) {
		return new ScreenHandlerBuilder("watersink").player(player.getInventory()).inventory().hotbar()
			.addInventory().blockEntity(this)
			.slot(CELL_INPUT_SLOT, 25, 35).outputSlot(CELL_OUTPUT_SLOT, 25, 55)
			.sync(tank)
			.addInventory().create(this, syncID);
	}

	// IToolDrop
	@Override
	public ItemStack getToolDrop(PlayerEntity playerIn) {
		return new ItemStack(TRContent.Machine.WATER_SINK);
	}
}
