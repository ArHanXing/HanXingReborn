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

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import reborncore.common.blockentity.MultiblockWriter;
import techreborn.TechReborn;
import techreborn.blockentity.machine.GenericMachineBlockEntity;
import techreborn.multiblock.MultiblockDefinition;
import techreborn.multiblock.MultiblockDefinitionLoader;
import techreborn.multiblock.MultiblockStructureTracker;

import java.util.function.BiPredicate;

/**
 * Base class for multiblock machines whose structure is driven by a JSON
 * definition instead of hard-coded code.
 * <p>
 * Subclasses only need to implement {@link #getMultiblockId()}; the actual
 * structure is looked up from {@link MultiblockDefinitionLoader} (see
 * {@code config/techreborn/multiblock/}).
 * <p>
 * The multiblock validity is cached (GTCEu style): the structure is only
 * re-verified when the cache expires (every {@link #CACHE_TTL_TICKS} ticks)
 * or when a block change inside the structure bounding box invalidates it
 * via {@link MultiblockStructureTracker}. This keeps large structures (e.g.
 * 9x9x9) cheap to check every tick.
 */
public abstract class JsonMultiblockMachineBlockEntity extends GenericMachineBlockEntity {

	/**
	 * How many ticks a cached validation result stays fresh. Matches the
	 * existing once-per-second forced recipe refresh in
	 * {@code RecipeCrafter#setIsActive()}, so behaviour is never worse than
	 * before while non-player block changes (pistons, explosions, water)
	 * converge within one second.
	 */
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

	public JsonMultiblockMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, String name,
			int maxInput, int maxEnergy, Block toolDrop, int energySlot) {
		super(type, pos, state, name, maxInput, maxEnergy, toolDrop, energySlot);
	}

	/**
	 * @return {@link String} the id used to look up this machine's JSON
	 *         structure definition (matches the file name in
	 *         {@code config/techreborn/multiblock/}).
	 */
	public abstract String getMultiblockId();

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

	/**
	 * Clears the cached validation result so the next
	 * {@link #isMultiblockValid()} call re-verifies the structure. Called by
	 * {@link MultiblockStructureTracker} when a block inside the structure
	 * bounding box changes.
	 */
	public void invalidateStructureCache() {
		multiblockValidCache = null;
		cacheTick = -1;
	}

	/**
	 * Cheap bounding-box test used to decide whether a world position could
	 * be part of this machine's structure.
	 *
	 * @param pos {@link BlockPos} the world position to test
	 * @return {@code true} if the position is inside the last verified
	 *         structure bounding box
	 */
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

		// Real verification. The bounding box writer also records the world
		// positions of every structure block so block-change events can be
		// cheaply filtered later.
		MultiblockWriter.MultiblockVerifier verifier = new MultiblockWriter.MultiblockVerifier(getPos(), world);
		BoundingBoxWriter boxWriter = new BoundingBoxWriter(getPos(), verifier);
		writeMultiblock(boxWriter.rotate(getFacing().getOpposite()));
		boolean valid = verifier.isValid();

		hasStructureBounds = boxWriter.hasBounds();
		if (hasStructureBounds) {
			structureMinX = boxWriter.minX;
			structureMinY = boxWriter.minY;
			structureMinZ = boxWriter.minZ;
			structureMaxX = boxWriter.maxX;
			structureMaxY = boxWriter.maxY;
			structureMaxZ = boxWriter.maxZ;
		}

		multiblockValidCache = valid;
		cacheTick = now;
		return valid;
	}

	@Override
	public void writeMultiblock(MultiblockWriter writer) {
		MultiblockDefinition definition = MultiblockDefinitionLoader.get(getMultiblockId());
		if (definition == null) {
			TechReborn.LOGGER.warn("No multiblock definition found for '{}', machine cannot be assembled",
					getMultiblockId());
			return;
		}
		definition.apply(writer);
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
}
