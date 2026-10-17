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

package techreborn.blockentity.machine.multiblock;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import reborncore.common.blockentity.MachineBaseBlockEntity;
import reborncore.common.fluid.FluidUtils;
import reborncore.common.fluid.FluidValue;
import reborncore.common.fluid.container.FluidInstance;
import reborncore.common.recipes.RecipeCrafter;
import reborncore.common.screen.BuiltScreenHandler;
import reborncore.common.screen.BuiltScreenHandlerProvider;
import reborncore.common.screen.builder.ScreenHandlerBuilder;
import reborncore.common.util.RebornInventory;
import reborncore.common.util.Tank;
import techreborn.config.TechRebornConfig;
import techreborn.init.ModRecipes;
import techreborn.init.TRBlockEntities;
import techreborn.init.TRContent;
import techreborn.recipe.recipes.VoidDrillRecipe;
import techreborn.world.VoidDrillData;

/**
 * The Void Drill: a multiblock that pumps the biome's fluid out of the chunk it
 * stands in.
 * <p>
 * <b>Per-chunk reserves.</b> Every chunk holds between 100 and 10 000 buckets of
 * whatever its biome yields. The full amount is derived from the world seed, so
 * only chunks that have actually been mined carry state
 * ({@link VoidDrillData}).
 * <p>
 * <b>Depletion.</b> A successful run debits {@code produced x factor} from the
 * chunk, where the factor starts at 100% and drops by 10 percentage points per
 * overclocker, down to a floor of {@value #MIN_DEPLETION}. The factor is the
 * fraction of what is pumped that the chunk actually loses; at 100% the drill is
 * a pure transfer, below that it slowly creates fluid. The last run of a chunk
 * always takes whatever is left, so a partially depleted chunk cannot trickle
 * forever.
 */
public class VoidDrillBlockEntity extends JsonMultiblockMachineBlockEntity implements BuiltScreenHandlerProvider {

	private static final String MULTIBLOCK_ID = "void_drill";

	/** Slot layout: containers in, filled containers out, battery. */
	private static final int CONTAINER_INPUT_SLOT = 0;
	private static final int CONTAINER_OUTPUT_SLOT = 1;
	private static final int ENERGY_SLOT = 2;

	/** Tank size, 128 buckets. */
	public static final FluidValue TANK_CAPACITY = FluidValue.BUCKET.multiply(128);

	/** Fraction of the produced fluid the chunk loses per run with no upgrades. */
	public static final double BASE_DEPLETION = 1.0;
	/** Depletion removed per overclocker, as a fraction. */
	public static final double DEPLETION_PER_OVERCLOCKER = 0.10;
	/** The chunk always loses at least this much, so it cannot run forever. */
	public static final double MIN_DEPLETION = 0.10;

	public final Tank tank;
	private int ticksSinceLastChange;
	/** Chunk reserve mirrored to the client for the GUI and tooltips. */
	private long syncedRemaining;

	public VoidDrillBlockEntity(BlockPos pos, BlockState state) {
		super(TRBlockEntities.VOID_DRILL, pos, state, "VoidDrill",
				TechRebornConfig.voidDrillMaxInput,
				TechRebornConfig.voidDrillMaxEnergy,
				TRContent.Machine.VOID_DRILL.block, ENERGY_SLOT);
		this.inventory = new RebornInventory<>(ENERGY_SLOT + 1, "VoidDrillBlockEntity", 64, this);
		// No item inputs and no item outputs: the run is resolved by the recipe's
		// onCraft hook, which pumps fluid into the tank.
		this.crafter = new RecipeCrafter(ModRecipes.VOID_DRILL, this, 0, 0, this.inventory, new int[0], new int[0]);
		this.tank = new Tank("VoidDrillBlockEntity", TANK_CAPACITY);
	}

	@Override
	public String getMultiblockId() {
		return MULTIBLOCK_ID;
	}

	// =======================================================================
	// depletion
	// =======================================================================

	/**
	 * Counts occupied upgrade slots, not items: like every other machine, an
	 * upgrade applies once per slot and the stack size is irrelevant. This is
	 * what the upgrade tooltip previews.
	 *
	 * @return {@code int} how many overclocker slots are filled (0..4)
	 */
	public int countOverclockers() {
		Inventory upgrades = getUpgradeInventory();
		if (upgrades == null) {
			return 0;
		}
		int count = 0;
		for (int i = 0; i < getUpgradeSlotCount(); i++) {
			ItemStack stack = upgrades.getStack(i);
			if (!stack.isEmpty() && stack.isOf(TRContent.Upgrades.OVERCLOCKER.asItem())) {
				count++;
			}
		}
		return count;
	}

	/**
	 * @return {@code double} fraction of the pumped fluid the chunk loses per
	 *         run, between {@value #MIN_DEPLETION} and
	 *         {@value #BASE_DEPLETION}
	 */
	public double depletionFactor() {
		return Math.max(MIN_DEPLETION, BASE_DEPLETION - DEPLETION_PER_OVERCLOCKER * countOverclockers());
	}

	// =======================================================================
	// chunk reserve
	// =======================================================================

	/**
	 * @return {@code long} millibuckets left in the chunk the controller sits in
	 */
	public long getChunkRemaining() {
		if (!(getWorld() instanceof ServerWorld serverWorld)) {
			return 0;
		}
		return VoidDrillData.remaining(serverWorld, new ChunkPos(getPos()));
	}

	/**
	 * @return {@code long} the reserve mirrored to the client, for display only
	 */
	public long getSyncedRemaining() {
		return syncedRemaining;
	}

	/**
	 * @param syncedRemaining {@code long} the client-side mirror
	 */
	public void setSyncedRemaining(long syncedRemaining) {
		this.syncedRemaining = syncedRemaining;
	}

	// =======================================================================
	// recipe gating
	// =======================================================================

	/**
	 * @param recipe {@link VoidDrillRecipe} the candidate recipe
	 * @return {@code boolean} whether the controller's biome matches
	 */
	public boolean matchesBiome(VoidDrillRecipe recipe) {
		World world = getWorld();
		if (world == null) {
			return false;
		}
		return world.getBiome(getPos()).getKey()
				.map(key -> key.getValue().equals(recipe.biome()))
				.orElse(false);
	}

	/**
	 * @param recipe {@link VoidDrillRecipe} the candidate recipe
	 * @return {@code boolean} whether a run could start right now
	 */
	public boolean canRun(VoidDrillRecipe recipe) {
		if (!isMultiblockValid() || !matchesBiome(recipe)) {
			return false;
		}
		long remaining = getChunkRemaining();
		if (remaining <= 0) {
			return false;
		}
		long producible = Math.min(recipe.amountMb(), remaining);
		return tank.canFit(recipe.fluid(), FluidValue.fromMillibuckets(producible));
	}

	/**
	 * Resolves a finished run: pumps the fluid into the tank and debits the
	 * chunk.
	 *
	 * @param recipe {@link VoidDrillRecipe} the run that finished
	 * @return {@code boolean} whether it produced anything; {@code false} keeps
	 *         the run alive so it retries once there is tank space again
	 */
	public boolean completeDrill(VoidDrillRecipe recipe) {
		if (!(getWorld() instanceof ServerWorld serverWorld)) {
			return false;
		}
		ChunkPos chunk = new ChunkPos(getPos());
		long remaining = VoidDrillData.remaining(serverWorld, chunk);
		if (remaining <= 0) {
			return false;
		}

		boolean lastRun = remaining <= recipe.amountMb();
		long produced = Math.min(recipe.amountMb(), remaining);
		FluidValue amount = FluidValue.fromMillibuckets(produced);
		if (!tank.canFit(recipe.fluid(), amount)) {
			return false;
		}

		tank.modifyFluid(instance -> instance.isEmptyFluid()
				? new FluidInstance(recipe.fluid(), amount)
				: instance.addAmount(amount));

		// The final run of a chunk takes whatever is left, so a below-100%
		// depletion factor cannot leave a chunk trickling forever.
		long consumed = lastRun ? remaining : Math.round(produced * depletionFactor());
		VoidDrillData.consume(serverWorld, chunk, consumed);
		syncedRemaining = Math.max(0, remaining - consumed);
		markDirty();
		return true;
	}

	// =======================================================================
	// tick
	// =======================================================================

	@Override
	public void tick(World world, BlockPos pos, BlockState state, MachineBaseBlockEntity blockEntity) {
		super.tick(world, pos, state, blockEntity);
		if (world == null || world.isClient) {
			return;
		}

		ticksSinceLastChange++;
		// Check cells twice per second, like the fluid replicator.
		if (ticksSinceLastChange >= 10) {
			if (!inventory.getStack(CONTAINER_INPUT_SLOT).isEmpty()) {
				FluidUtils.fillContainers(tank, inventory, CONTAINER_INPUT_SLOT, CONTAINER_OUTPUT_SLOT);
			}
			syncedRemaining = getChunkRemaining();
			ticksSinceLastChange = 0;
		}
	}

	// =======================================================================
	// persistence / GUI
	// =======================================================================

	@Override
	public void readNbt(NbtCompound tagCompound, RegistryWrapper.WrapperLookup registryLookup) {
		super.readNbt(tagCompound, registryLookup);
		tank.read(tagCompound, registryLookup);
	}

	@Override
	public void writeNbt(NbtCompound tagCompound, RegistryWrapper.WrapperLookup registryLookup) {
		super.writeNbt(tagCompound, registryLookup);
		tank.write(tagCompound, registryLookup);
	}

	@Nullable
	@Override
	public Tank getTank() {
		return tank;
	}

	@Override
	public BuiltScreenHandler createScreenHandler(int syncID, final PlayerEntity player) {
		return new ScreenHandlerBuilder("voiddrill").player(player.getInventory()).inventory().hotbar().addInventory()
				.blockEntity(this)
				.fluidSlot(CONTAINER_INPUT_SLOT, 124, 35)
				.outputSlot(CONTAINER_OUTPUT_SLOT, 124, 55)
				.energySlot(ENERGY_SLOT, 8, 72)
				.sync(tank).syncEnergyValue().syncCrafterValue()
				.sync(PacketCodecs.VAR_LONG, this::getSyncedRemaining, this::setSyncedRemaining)
				.addInventory().create(this, syncID);
	}
}
