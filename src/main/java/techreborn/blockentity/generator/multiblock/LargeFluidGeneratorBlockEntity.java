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
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import reborncore.common.blockentity.MachineBaseBlockEntity;
import reborncore.common.blockentity.MultiblockWriter;
import reborncore.common.fluid.FluidUtils;
import reborncore.common.fluid.FluidValue;
import reborncore.common.util.Tank;
import techreborn.blockentity.generator.BaseFluidGeneratorBlockEntity;
import techreborn.config.TechRebornConfig;
import techreborn.init.ModFluids;
import techreborn.multiblock.IMultiblockStructureMember;
import techreborn.multiblock.MultiblockDefinition;
import techreborn.multiblock.MultiblockDefinitionLoader;
import techreborn.multiblock.MultiblockStructureTracker;
import net.minecraft.text.Text;
import java.util.List;
import techreborn.blockentity.generator.GeneratorTooltipHelper;

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
 *   <li>Oxygen cells - an oxygen cell in the input slot boosts the output by
 *       {@value #OXYGEN_POWER_MULTIPLIER}x for {@value #OXYGEN_DURATION_TICKS}
 *       ticks, then the empty cell is ejected into the output slot.</li>
 * </ul>
 * The max output, energy buffer and energy per tick of each generator are
 * configured through {@code generators.json} (see the per-machine config
 * entries in {@link TechRebornConfig}).
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

	/** Remaining ticks of the oxygen boost. */
	private int oxygenTicks = 0;
	private int oxygenCheckCounter = 0;

	/** Tank indices, also the order the fluid IO config lists them in. */
	public static final int TANK_FUEL = 0;
	public static final int TANK_PROPELLANT = 1;

	/** Capacity of the compressed air tank. */
	public static final FluidValue PROPELLANT_CAPACITY = FluidValue.BUCKET.multiply(64);

	/**
	 * Compressed air consumed per boost window. One bucket buys
	 * {@link #OXYGEN_DURATION_TICKS} ticks, the same trade as a compressed air
	 * cell, so the tank is simply the bulk way to feed the same mechanic.
	 */
	public static final FluidValue PROPELLANT_PER_BOOST = FluidValue.BUCKET;

	/**
	 * The oxidiser tank: compressed air burned for the output boost.
	 * <p>
	 * Kept apart from the fuel tank so the two never mix, and so the per-tank
	 * fluid IO config can pipe them separately.
	 */
	public final Tank propellantTank;

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
			RecipeType<FluidGeneratorRecipe> type, String blockEntityName, int euTick) {
		super(blockEntityType, pos, state, type, blockEntityName, FluidValue.BUCKET.multiply(160), euTick);
		this.propellantTank = new Tank(blockEntityName + "Propellant", PROPELLANT_CAPACITY);
	}

	/**
	 * Tank 0 is the fuel, tank 1 the compressed air that drives the boost.
	 */
	@Override
	public int getTankCount() {
		return 2;
	}

	@Override
	@Nullable
	public Tank getTank(int index) {
		return switch (index) {
			case TANK_FUEL -> tank;
			case TANK_PROPELLANT -> propellantTank;
			default -> null;
		};
	}

	/**
	 * Compressed air belongs in the oxidiser tank, everything burnable in the fuel
	 * tank, so a pipe hooked up to the machine fills the right one instead of
	 * whichever tank is empty first.
	 */
	@Override
	public int preferredTankFor(FluidVariant variant) {
		if (variant.getFluid().matchesType(ModFluids.COMPRESSED_AIR.getFluid())) {
			return TANK_PROPELLANT;
		}
		return TANK_FUEL;
	}

	/**
	 * @return {@code boolean} whether compressed air boosts this generator. Machines
	 *         with their own use for the air tank (the rocket engine burns it as a
	 *         hard requirement instead) opt out.
	 */
	protected boolean usesPropellantBoost() {
		return true;
	}

	// Explicitly re-declared even though BlockEntity already provides them:
	// after yarn->intermediary remapping the interface methods (whose names
	// are not remapped) no longer match the renamed BlockEntity methods, so
	// calls through IMultiblockStructureMember would hit an AbstractMethodError.
	@Override
	public BlockPos getPos() {
		return super.getPos();
	}

	@Override
	@Nullable
	public World getWorld() {
		return super.getWorld();
	}

	/**
	 * @return {@link String} the JSON multiblock definition id (file name in
	 *         {@code config/techreborn/multiblock/}).
	 */
	public abstract String getMultiblockId();

	/**
	 * @return the configured max output (EU/t) of this generator, from
	 *         {@code generators.json}
	 */
	protected abstract int getConfiguredMaxOutput();

	/**
	 * @return the configured energy buffer (EU) of this generator, from
	 *         {@code generators.json}
	 */
	protected abstract int getConfiguredMaxEnergy();

	@Override
	public void addInfo(List<Text> info, boolean isReal, boolean hasData) {
		super.addInfo(info, isReal, hasData);
		GeneratorTooltipHelper.addGenerationRate(info, super.getEuPerTick());
	}

	@Override
	public long getBaseMaxOutput() {
		return getConfiguredMaxOutput();
	}

	@Override
	public long getBaseMaxPower() {
		return getConfiguredMaxEnergy();
	}

	@Override
	protected int getEuPerTick() {
		float multiplier = oxygenTicks > 0 ? OXYGEN_POWER_MULTIPLIER : 1.0F;
		return (int) (super.getEuPerTick() * multiplier);
	}

	/**
	 * @return {@code true} while a compressed air cell boost is active,
	 *         multiplying the EU/t output by {@link #OXYGEN_POWER_MULTIPLIER}.
	 */
	public boolean isOxygenBoosted() {
		return oxygenTicks > 0;
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
		// Top the boost up when it has run out: the compressed air tank first, then
		// a cell from the input slot as the fallback, so a machine plumbed for air
		// no longer needs to be fed cells.
		if (!usesPropellantBoost() || oxygenTicks > 0) {
			return;
		}
		if (++oxygenCheckCounter < OXYGEN_CHECK_INTERVAL) {
			return;
		}
		oxygenCheckCounter = 0;

		if (consumePropellant()) {
			oxygenTicks = OXYGEN_DURATION_TICKS;
			return;
		}

		// Cell fallback: one cell = 1 second of boost, the empty cell is ejected
		// into the output slot.
		ItemStack stack = inventory.getStack(0);
		if (!stack.isEmpty() && FluidUtils.containsMatchingFluid(stack, f -> f == ModFluids.COMPRESSED_AIR.getFluid())) {
			// voidFluid=true: extract the air without filling the fuel tank
			if (FluidUtils.drainContainers(tank, inventory, 0, 1, true)) {
				oxygenTicks = OXYGEN_DURATION_TICKS;
			}
		}
	}

	/**
	 * Takes one boost's worth of compressed air out of the propellant tank.
	 *
	 * @return {@code boolean} whether the tank had enough air
	 */
	private boolean consumePropellant() {
		if (!propellantTank.getFluid().matchesType(ModFluids.COMPRESSED_AIR.getFluid())) {
			return false;
		}
		if (propellantTank.getFluidAmount().lessThan(PROPELLANT_PER_BOOST)) {
			return false;
		}
		propellantTank.modifyFluid(instance -> instance.subtractAmount(PROPELLANT_PER_BOOST));
		return true;
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
		propellantTank.read(tagCompound.getCompound("propellantTank"), registryLookup);
	}

	@Override
	public void writeNbt(NbtCompound tagCompound, RegistryWrapper.WrapperLookup registryLookup) {
		super.writeNbt(tagCompound, registryLookup);
		tagCompound.putInt("oxygenTicks", oxygenTicks);
		NbtCompound propellant = new NbtCompound();
		propellantTank.write(propellant, registryLookup);
		tagCompound.put("propellantTank", propellant);
	}
}
