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

package techreborn.blockentity.generator.multiblock;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.recipe.RecipeType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import reborncore.common.blockentity.MachineBaseBlockEntity;
import reborncore.common.blockentity.MultiblockWriter;
import reborncore.common.fluid.FluidUtils;
import reborncore.common.fluid.FluidValue;
import techreborn.blockentity.generator.BaseFluidGeneratorBlockEntity;
import techreborn.config.TechRebornConfig;
import techreborn.init.ModFluids;
import techreborn.multiblock.IMultiblockStructureMember;
import techreborn.multiblock.MultiblockDefinition;
import techreborn.multiblock.MultiblockDefinitionLoader;
import techreborn.multiblock.MultiblockStructureTracker;
import techreborn.recipe.recipes.FluidGeneratorRecipe;

import java.util.function.BiPredicate;

/**
 * Base class for the large multiblock fluid generators (Large Gas Turbine,
 * Large Combustion Engine, Universal Chemical Fuel Engine).
 * <p>
 * The structure is driven by a JSON definition (like every other JSON
 * multiblock machine), but because fluid generators extend
 * {@link BaseFluidGeneratorBlockEntity} (a power producer, not a machine
 * consumer) the structure validation logic is self-contained here instead of
 * inheriting {@code JsonMultiblockMachineBlockEntity}.
 * <p>
 * Common mechanics:
 * <ul>
 *   <li>{@code parallel} - the generator burns {@code parallel} fuel streams at
 *       once, multiplying both energy output and fuel consumption.</li>
 *   <li>{@code heatValueMultiplier} - optional fuel efficiency bonus (e.g.
 *       1.25x: same fuel produces more energy).</li>
 *   <li>Oxygen cells - an oxygen cell in the input slot boosts the output by
 *       {@value #OXYGEN_POWER_MULTIPLIER}x for {@value #OXYGEN_DURATION_TICKS}
 *       ticks, then the empty cell is ejected into the output slot.</li>
 * </ul>
 * The max output/power are not hard-coded: they are derived from the small gas
 * and diesel generator configs (min value x parallel).
 */
public abstract class LargeFluidGeneratorBlockEntity extends BaseFluidGeneratorBlockEntity implements IMultiblockStructureMember {

	/**
	 * Compressed air cell boost: x1.5 power for 1 second (20 ticks) per cell.
	 * (In TechReborn lore, compressed air is the same as oxygen.)
	 */
	public static final float OXYGEN_POWER_MULTIPLIER = 1.5F;
	public static final int OXYGEN_DURATION_TICKS = 20;
	/** How often the compressed air cell input is checked (ticks). */
	private static final int OXYGEN_CHECK_INTERVAL = 10;

	/** Parallel fuel streams. */
	protected final int parallel;
	/** Fuel efficiency bonus applied to the base energy per tick. */
	protected final float heatValueMultiplier;
	/** Remaining ticks of the oxygen boost. */
	private int oxygenTicks = 0;
	private int oxygenCheckCounter = 0;

	// Multiblock validation cache (self-contained copy of the JSON multiblock logic)
	private static final int CACHE_TTL_TICKS = 20;
	@Nullable
	private Boolean multiblockValidCache = null;
	private long cacheTick = -1;

	// World-space bounding box of the structure, used to cheaply decide if a
	// changed block position can possibly belong to this machine.
	private boolean hasStructureBounds = false;
	private int structureMinX = Integer.MAX_VALUE;
	private int structureMinY = Integer.MAX_VALUE;
	private int structureMinZ = Integer.MAX_VALUE;
	private int structureMaxX = Integer.MIN_VALUE;
	private int structureMaxY = Integer.MIN_VALUE;
	private int structureMaxZ = Integer.MIN_VALUE;

	public LargeFluidGeneratorBlockEntity(BlockEntityType<?> blockEntityType, BlockPos pos, BlockState state,
			RecipeType<FluidGeneratorRecipe> type, String blockEntityName, int baseEuPerTick,
			int parallel, float heatValueMultiplier) {
		super(blockEntityType, pos, state, type, blockEntityName, FluidValue.BUCKET.multiply(10),
				(int) (baseEuPerTick * parallel * heatValueMultiplier));
		this.parallel = parallel;
		this.heatValueMultiplier = heatValueMultiplier;
	}

	/**
	 * @return {@link String} the JSON multiblock definition id (file name in
	 *         {@code config/techreborn/multiblock/}).
	 */
	public abstract String getMultiblockId();

	/**
	 * Shared "small generator output" reference: min of the small gas turbine
	 * and small diesel generator max outputs, so the large generators are
	 * balanced against both small machines without hard-coding values.
	 */
	protected static int baseMaxOutput() {
		return Math.min(TechRebornConfig.gasTurbineMaxOutput, TechRebornConfig.dieselGeneratorMaxOutput);
	}

	protected static int baseMaxEnergy() {
		return Math.min(TechRebornConfig.gasTurbineMaxEnergy, TechRebornConfig.dieselGeneratorMaxEnergy);
	}

	protected static int baseEuPerTick() {
		return Math.min(TechRebornConfig.gasTurbineEnergyPerTick, TechRebornConfig.dieselGeneratorEnergyPerTick);
	}

	@Override
	public long getBaseMaxOutput() {
		return (long) baseMaxOutput() * parallel;
	}

	@Override
	public long getBaseMaxPower() {
		return (long) baseMaxEnergy() * parallel;
	}

	@Override
	protected int getEuPerTick() {
		float multiplier = oxygenTicks > 0 ? OXYGEN_POWER_MULTIPLIER : 1.0F;
		return (int) (super.getEuPerTick() * multiplier);
	}

	/**
	 * The machine only generates energy while its multiblock structure is
	 * valid: fuel is neither burned nor converted otherwise.
	 */
	@Override
	protected boolean tryAddingEnergy(int amount) {
		if (!isMultiblockValid()) {
			return false;
		}
		return super.tryAddingEnergy(amount);
	}

	@Override
	public void tick(World world, BlockPos pos, BlockState state, MachineBaseBlockEntity blockEntity) {
		super.tick(world, pos, state, blockEntity);
		if (world == null || world.isClient) {
			return;
		}
		if (oxygenTicks > 0) {
			oxygenTicks--;
		}
		// Feed compressed air cells from the input slot: one cell = 1 second of
		// boost, the empty cell is ejected into the output slot.
		if (++oxygenCheckCounter >= OXYGEN_CHECK_INTERVAL) {
			oxygenCheckCounter = 0;
			ItemStack stack = inventory.getStack(0);
			if (!stack.isEmpty() && FluidUtils.containsMatchingFluid(stack, f -> f == ModFluids.COMPRESSED_AIR.getFluid())) {
				// voidFluid=true: extract the air without filling the fuel tank
				if (FluidUtils.drainContainers(tank, inventory, 0, 1, true)) {
					oxygenTicks = OXYGEN_DURATION_TICKS;
				}
			}
		}
	}

	@Override
	public void onLoad() {
		super.onLoad();
		MultiblockStructureTracker.register(this);
	}

	@Override
	public void markRemoved() {
		MultiblockStructureTracker.unregister(this);
		super.markRemoved();
	}

	@Override
	public void writeMultiblock(MultiblockWriter writer) {
		MultiblockDefinition definition = MultiblockDefinitionLoader.get(getMultiblockId());
		if (definition == null) {
			return;
		}
		definition.apply(writer);
	}

	@Override
	public void invalidateStructureCache() {
		multiblockValidCache = null;
		cacheTick = -1;
	}

	@Override
	public boolean isPositionInStructure(BlockPos pos) {
		if (!hasStructureBounds) {
			return false;
		}
		return pos.getX() >= structureMinX && pos.getX() <= structureMaxX
				&& pos.getY() >= structureMinY && pos.getY() <= structureMaxY
				&& pos.getZ() >= structureMinZ && pos.getZ() <= structureMaxZ;
	}

	@Override
	public boolean isMultiblockValid() {
		World world = getWorld();
		if (world == null) {
			return false;
		}
		long now = world.getTime();
		if (multiblockValidCache != null && now - cacheTick < CACHE_TTL_TICKS) {
			return multiblockValidCache;
		}
		MultiblockWriter.MultiblockVerifier verifier = new MultiblockWriter.MultiblockVerifier(getPos(), world);
		BoundingBoxWriter boxWriter = new BoundingBoxWriter(getPos(), verifier);
		MultiblockDefinition definition = MultiblockDefinitionLoader.get(getMultiblockId());
		if (definition == null) {
			multiblockValidCache = false;
			cacheTick = now;
			return false;
		}
		definition.apply(boxWriter.rotate(getFacing().getOpposite()));
		hasStructureBounds = boxWriter.hasBounds();
		if (hasStructureBounds) {
			structureMinX = boxWriter.minX;
			structureMinY = boxWriter.minY;
			structureMinZ = boxWriter.minZ;
			structureMaxX = boxWriter.maxX;
			structureMaxY = boxWriter.maxY;
			structureMaxZ = boxWriter.maxZ;
		}
		multiblockValidCache = verifier.isValid();
		cacheTick = now;
		return multiblockValidCache;
	}

	/**
	 * Wraps another writer and records the world-space bounding box of every
	 * block written to it. Used while verifying so the structure bounds are
	 * kept up to date on every re-validation.
	 */
	private static final class BoundingBoxWriter implements MultiblockWriter {

		private final BlockPos origin;
		private final MultiblockWriter delegate;

		private boolean hasBounds = false;
		private int minX = Integer.MAX_VALUE;
		private int minY = Integer.MAX_VALUE;
		private int minZ = Integer.MAX_VALUE;
		private int maxX = Integer.MIN_VALUE;
		private int maxY = Integer.MIN_VALUE;
		private int maxZ = Integer.MIN_VALUE;

		BoundingBoxWriter(BlockPos origin, MultiblockWriter delegate) {
			this.origin = origin;
			this.delegate = delegate;
		}

		boolean hasBounds() {
			return hasBounds;
		}

		@Override
		public MultiblockWriter add(int x, int y, int z, BiPredicate<BlockView, BlockPos> predicate, BlockState state) {
			BlockPos worldPos = origin.add(x, y, z);
			hasBounds = true;
			minX = Math.min(minX, worldPos.getX());
			minY = Math.min(minY, worldPos.getY());
			minZ = Math.min(minZ, worldPos.getZ());
			maxX = Math.max(maxX, worldPos.getX());
			maxY = Math.max(maxY, worldPos.getY());
			maxZ = Math.max(maxZ, worldPos.getZ());
			return delegate.add(x, y, z, predicate, state);
		}
	}

	@Override
	public void readNbt(NbtCompound tagCompound, RegistryWrapper.WrapperLookup registryLookup) {
		super.readNbt(tagCompound, registryLookup);
		oxygenTicks = tagCompound.getInt("oxygenTicks");
	}

	@Override
	public void writeNbt(NbtCompound tagCompound, RegistryWrapper.WrapperLookup registryLookup) {
		super.writeNbt(tagCompound, registryLookup);
		tagCompound.putInt("oxygenTicks", oxygenTicks);
	}
}
