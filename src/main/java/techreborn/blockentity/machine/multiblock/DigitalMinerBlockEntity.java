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

package techreborn.blockentity.machine.multiblock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import reborncore.common.blockentity.MachineBaseBlockEntity;
import reborncore.common.chunkloading.ChunkLoaderManager;
import reborncore.common.screen.BuiltScreenHandler;
import reborncore.common.screen.BuiltScreenHandlerProvider;
import reborncore.common.screen.builder.BlockEntityScreenHandlerBuilder;
import reborncore.common.screen.builder.ScreenHandlerBuilder;
import reborncore.common.util.RebornInventory;

import techreborn.config.TechRebornConfig;
import techreborn.init.TRBlockEntities;
import techreborn.init.TRContent;

/**
 * The Digital Miner: a JSON multiblock that mines every block matching a
 * player-supplied filter inside a configurable chunk radius around itself.
 * <p>
 * <b>Filtering.</b> The GUI takes a single expression. An expression starting
 * with {@code #} is a <i>block tag</i>; every other expression is a regular
 * expression matched against the block's registry id. In both forms {@code *}
 * is expanded to {@code .*} so that the idiomatic {@code #c:ores/*} and
 * {@code techreborn:*_ore} both do what they look like they do.
 * <p>
 * <b>Scanning.</b> A full pass over the working area is far too expensive to do
 * in one tick (radius 3 is 49 chunk columns), so the region is walked with a
 * cursor that examines a bounded number of positions per tick. Because several
 * hundred thousand positions can share only a handful of distinct block states,
 * the filter is evaluated once per {@link BlockState} and memoised, which turns
 * the inner loop into a single hash lookup.
 * <p>
 * <b>Selection.</b> Only blocks that yield at least one item are ever queued:
 * fluids, unbreakable blocks and anything whose loot table is empty are skipped,
 * so a pass always makes progress. Harvesting is all-or-nothing, which means a
 * full output leaves the block in place and parks the machine instead of
 * burning power on a harvest it cannot store.
 * <p>
 * <b>Power.</b> The draw is constant while mining and scales in exact powers of
 * two with the number of overclocker upgrades (0.25/0.5/1/2/4 A at LV), which
 * is why this machine reads the upgrade inventory directly instead of using the
 * generic overclocker multipliers.
 */
public class DigitalMinerBlockEntity extends JsonMultiblockMachineBlockEntity implements BuiltScreenHandlerProvider {

	private static final String DEFAULT_FILTER = "#c:ores/*";

	/** Output slots 0..7. */
	public static final int OUTPUT_SLOTS = 8;
	/** Upgrade slots are virtual: they live in the base class upgrade inventory. */
	private static final int VIRTUAL_UPGRADE_SLOTS = 4;
	private static final int FILTER_SLOT = 12;
	private static final int ENERGY_SLOT = 13;

	private int radius = 1;

	private String filterText = DEFAULT_FILTER;
	@Nullable
	private transient CompiledFilter filter;
	/** Set when the user typed an expression that does not compile. */
	private boolean filterInvalid = false;

	/** Positions that still need mining, nearest-first per scan order. */
	private final List<BlockPos> targets = new ArrayList<>();

	/**
	 * Per-pass memoisation of the filter result keyed by block state. A scan
	 * pass visits ~10^6 positions drawn from only a few dozen distinct states,
	 * so evaluating the regex once per state instead of once per position turns
	 * the inner loop into a single hash lookup. Cleared by {@link #resetScan()}.
	 */
	private final Map<BlockState, Boolean> matchCache = new HashMap<>();

	// ---- scan cursor over the whole working region -------------------------
	/** Index of the chunk column currently being walked. */
	private int scanChunk = 0;
	private int scanX = 0;
	private int scanZ = 0;
	private int scanY = Integer.MIN_VALUE;
	/** {@code false} until the first full pass finished. */
	private boolean scanCompleted = false;

	@Nullable
	private BlockPos miningPos = null;
	private int miningProgress = 0;

	private int displayTargetCount = 0;
	private int displaySpeedTicks = 0;
	/** First few matched blocks, for the GUI preview. */
	private String previewText = "";

	// ---- chunk force-loading ----------------------------------------------
	private boolean chunksLoaded = false;
	private int loadedRadius = -1;
	/**
	 * UUID of the player who placed the machine. {@code ChunkLoaderManager}
	 * rejects a blank owner ({@code Validate.isTrue(!StringUtils.isBlank(player))}),
	 * so this must never be null or empty when chunks are loaded.
	 */
	private String ownerUdid = "";

	public DigitalMinerBlockEntity(BlockPos pos, BlockState state) {
		super(TRBlockEntities.DIGITAL_MINER, pos, state, "DigitalMiner",
				TechRebornConfig.digitalMinerMaxInput,
				TechRebornConfig.digitalMinerMaxEnergy,
				TRContent.Machine.DIGITAL_MINER.block, ENERGY_SLOT);
		this.inventory = new RebornInventory<>(OUTPUT_SLOTS + VIRTUAL_UPGRADE_SLOTS + 2,
				"DigitalMinerBlockEntity", 64, this);
	}

	@Override
	public String getMultiblockId() {
		return "digital_miner";
	}

	/**
	 * Kept {@code true} so RebornCore renders the four upgrade slots and accepts
	 * overclocker items. The miner deliberately ignores the multipliers that
	 * pipeline applies: it derives its own speed and power from
	 * {@link #overclockerCount()} so the draw steps exactly 0.25/0.5/1/2/4 A
	 * instead of the generic 1.25^n compounding.
	 */
	@Override
	public boolean canBeUpgraded() {
		return true;
	}

	// =======================================================================
	// filter
	// =======================================================================

	/**
	 * A compiled block filter: either a tag lookup or a registry-id regex.
	 */
	private static final class CompiledFilter {
		@Nullable
		final TagKey<Block> tag;
		@Nullable
		final Pattern pattern;

		private CompiledFilter(@Nullable TagKey<Block> tag, @Nullable Pattern pattern) {
			this.tag = tag;
			this.pattern = pattern;
		}

		boolean matches(BlockState state) {
			if (tag != null) {
				return state.isIn(tag);
			}
			if (pattern == null) {
				return false;
			}
			Identifier id = net.minecraft.registry.Registries.BLOCK.getId(state.getBlock());
			return pattern.matcher(id.toString()).matches();
		}
	}

	/**
	 * Compiles a filter expression.
	 * <p>
	 * {@code #namespace:path} is treated as a block tag; a trailing {@code /*}
	 * is stripped because tags are matched by namespace membership rather than
	 * by glob. Anything else is a regex over the registry id, with bare
	 * {@code *} expanded to {@code .*} and {@code ?} to {@code .}.
	 *
	 * @param expression {@link String} the raw GUI text
	 * @return {@link CompiledFilter} the compiled filter
	 * @throws PatternSyntaxException if a regex branch does not compile
	 */
	private static CompiledFilter compile(String expression) {
		String text = expression.trim();
		if (text.isEmpty()) {
			return new CompiledFilter(null, Pattern.compile(".*"));
		}
		if (text.startsWith("#")) {
			String tagText = text.substring(1);
			// "#c:ores/*" -> the trailing wildcard is implicit for tags
			if (tagText.endsWith("/*")) {
				tagText = tagText.substring(0, tagText.length() - 2);
			}
			Identifier tagId;
			try {
				tagId = Identifier.of(tagText);
			} catch (IllegalArgumentException e) {
				throw new PatternSyntaxException("invalid tag id", tagText, 0);
			}
			return new CompiledFilter(TagKey.of(RegistryKeys.BLOCK, tagId), null);
		}
		String regex = globToRegex(text);
		return new CompiledFilter(null, Pattern.compile(regex));
	}

	/**
	 * Expands glob wildcards into regex syntax without touching regex
	 * metacharacters the user may have written deliberately.
	 *
	 * @param glob {@link String} the raw expression
	 * @return {@link String} a regex that matches the whole registry id
	 */
	private static String globToRegex(String glob) {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < glob.length(); i++) {
			char c = glob.charAt(i);
			switch (c) {
				case '*' -> out.append(".*");
				case '?' -> out.append('.');
				// escape the regex metacharacters that are not also glob syntax
				// so that a bare "techreborn:tin_ore" is a literal match
				case '.', '(', ')', '[', ']', '{', '}', '+', '^', '$', '|', '\\' -> out.append('\\').append(c);
				default -> out.append(c);
			}
		}
		return out.toString();
	}

	@Nullable
	private CompiledFilter filter() {
		if (filter == null && !filterInvalid) {
			try {
				filter = compile(filterText);
			} catch (IllegalArgumentException e) {
				filterInvalid = true;
			}
		}
		return filter;
	}

	public String getFilterText() {
		return filterText;
	}

	public boolean isFilterInvalid() {
		return filterInvalid;
	}

	/**
	 * Applies a new filter expression from the GUI and restarts the scan so the
	 * new selection takes effect from the beginning of the region.
	 *
	 * @param text {@link String} the raw expression typed by the player
	 */
	public void setFilterText(String text) {
		String next = text == null ? "" : text;
		if (next.equals(filterText)) {
			return;
		}
		filterText = next;
		filter = null;
		filterInvalid = false;
		// compile eagerly so an invalid expression is flagged right away
		filter();
		resetScan();
		syncWithAll();
	}

	// =======================================================================
	// range
	// =======================================================================

	public int getRadius() {
		return radius;
	}

	public void setRadius(int value) {
		this.radius = Math.max(1, Math.min(TechRebornConfig.digitalMinerMaxRadius, value));
	}

	/**
	 * Handles the +/- buttons of the GUI. Changing the radius invalidates every
	 * scan cursor and unloads the previously force-loaded chunks.
	 *
	 * @param delta  {@code int} how much to add to the radius
	 * @param player {@link PlayerEntity} the interacting player, may be {@code null}
	 */
	public void handleGuiInputFromClient(int delta, @Nullable PlayerEntity player) {
		int before = radius;
		setRadius(radius + delta);
		if (radius != before) {
			resetScan();
			unloadChunks();
			syncWithAll();
		}
		if (player instanceof ServerPlayerEntity serverPlayer) {
			serverPlayer.sendMessage(Text.translatable("gui.techreborn.digital_miner.radius",
					radius, radius * 2 + 1), true);
		}
	}

	private void resetScan() {
		targets.clear();
		matchCache.clear();
		miningPos = null;
		miningProgress = 0;
		scanChunk = 0;
		scanX = 0;
		scanZ = 0;
		scanY = Integer.MIN_VALUE;
		scanCompleted = false;
		displayTargetCount = 0;
		previewText = "";
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

		boolean structureOk = isMultiblockValid();
		CompiledFilter compiled = filter();
		boolean filterOk = compiled != null;

		if (!structureOk || !filterOk) {
			if (miningPos != null) {
				miningPos = null;
				miningProgress = 0;
			}
			setActiveState(world, pos, false);
			// keep only our own chunk loaded so a repaired structure or a new
			// filter is noticed, drop the working area
			updateChunks((ServerWorld) world, false);
			refreshDisplay(compiled);
			return;
		}

		// Constant draw while mining: the machine must be able to pay for the
		// whole tick before it makes any progress.
		long cost = energyPerTick();
		boolean powered = getEnergy() >= cost;
		if (powered) {
			useEnergy(cost);
		} else {
			if (miningPos != null) {
				miningPos = null;
				miningProgress = 0;
			}
			setActiveState(world, pos, false);
			refreshDisplay(compiled);
			return;
		}

		scanStep((ServerWorld) world, compiled);

		// "Working" drives both the front texture and the force-loaded area. A
		// pending target counts as work, except when the block is sitting ready
		// to harvest but the outputs cannot take it: that is the "outputs full,
		// stop" state, so the area is released instead.
		boolean pendingTarget = !targets.isEmpty() && miningPos == null;
		boolean harvested = mineStep((ServerWorld) world);
		boolean working = harvested || pendingTarget || !scanCompleted;
		setActiveState(world, pos, working);

		// Force-load the working area while there is something to do; when idle
		// (outputs full or nothing left to mine) only our own chunk stays
		// loaded, so the machine can still notice new targets.
		updateChunks((ServerWorld) world, working);

		refreshDisplay(compiled);
		if (world.getTime() % 20 == 0) {
			syncWithAll();
		}
	}

	private void setActiveState(World world, BlockPos pos, boolean active) {
		BlockState state = world.getBlockState(pos);
		if (state.getBlock() instanceof reborncore.common.blocks.BlockMachineBase machine) {
			machine.setActive(active, world, pos);
		}
	}

	/**
	 * Draws power for one tick of work.
	 *
	 * @return {@code long} EU consumed per tick at the current overclocker count
	 */
	private long energyPerTick() {
		double multiplier = Math.pow(TechRebornConfig.digitalMinerPowerMultiplierPerOverclocker, overclockerCount());
		return (long) Math.max(1, Math.round(TechRebornConfig.digitalMinerBaseEnergyPerTick * multiplier));
	}

	/**
	 * @return {@code int} ticks needed to break one block at the current
	 *         overclocker count
	 */
	public int breakTimeTicks() {
		double multiplier = Math.pow(TechRebornConfig.digitalMinerSpeedMultiplierPerOverclocker, overclockerCount());
		return (int) Math.max(1, Math.round(TechRebornConfig.digitalMinerBaseBreakTime * multiplier));
	}

	/**
	 * @return {@code int} how many overclocker upgrades are installed (0..4)
	 */
	public int overclockerCount() {
		int count = 0;
		for (int i = 0; i < VIRTUAL_UPGRADE_SLOTS; i++) {
			ItemStack stack = getUpgradeInventory().getStack(i);
			if (!stack.isEmpty() && stack.isOf(TRContent.Upgrades.OVERCLOCKER.asItem())) {
				count++;
			}
		}
		return count;
	}

	// =======================================================================
	// scanning
	// =======================================================================

	/**
	 * Examines up to the configured budget of positions, appending matches to
	 * the target list.
	 * <p>
	 * The cursor survives across ticks so the region is walked exactly once per
	 * pass. Because a pass touches on the order of a million positions that
	 * share only a few dozen distinct {@link BlockState}s, the filter is
	 * evaluated once per state and memoised in {@link #matchCache}; the inner
	 * loop then costs one world lookup plus one hash lookup.
	 *
	 * @param world    {@link ServerWorld} the machine's world
	 * @param compiled {@link CompiledFilter} the active filter
	 */
	private void scanStep(ServerWorld world, CompiledFilter compiled) {
		if (scanCompleted) {
			return;
		}
		int originX = (getPos().getX() >> 4 << 4) - radius * 16;
		int originZ = (getPos().getZ() >> 4 << 4) - radius * 16;
		int minY = world.getBottomY();
		// getTopY() is exclusive
		int maxY = world.getTopY() - 1;

		int budget = Math.max(1, TechRebornConfig.digitalMinerScanBudgetPerTick);
		int side = radius * 2 + 1;

		while (budget > 0 && !scanCompleted) {
			if (scanY == Integer.MIN_VALUE) {
				scanY = minY;
			}
			BlockPos probe = new BlockPos(originX + scanChunk % side * 16 + scanX,
					scanY,
					originZ + scanChunk / side * 16 + scanZ);
			budget--;

			BlockState probeState = world.getBlockState(probe);
			if (isMineable(probeState) && matchesCached(probeState, compiled) && hasDrops(world, probe, probeState)) {
				targets.add(probe.toImmutable());
			}

			// advance the cursor
			scanY++;
			if (scanY > maxY) {
				scanY = minY;
				scanX++;
				if (scanX >= 16) {
					scanX = 0;
					scanZ++;
					if (scanZ >= 16) {
						scanZ = 0;
						scanChunk++;
						if (scanChunk >= side * side) {
							scanCompleted = true;
						}
					}
				}
			}
		}
	}

	/**
	 * Evaluates the filter for a block state, reusing earlier results. The
	 * cache is per pass: {@link #resetScan()} drops it whenever the filter, the
	 * radius or the structure changes.
	 *
	 * @param state    {@link BlockState} the state to test
	 * @param compiled {@link CompiledFilter} the active filter
	 * @return {@code true} if the state matches the filter
	 */
	private boolean matchesCached(BlockState state, CompiledFilter compiled) {
		Boolean cached = matchCache.get(state);
		if (cached != null) {
			return cached;
		}
		boolean result = compiled.matches(state);
		matchCache.put(state, result);
		return result;
	}

	/**
	 * @param world {@link ServerWorld} the machine's world
	 * @param pos   {@link BlockPos} the position being probed
	 * @param state {@link BlockState} the state at that position
	 * @return {@code true} if the block yields at least one item
	 */
	private boolean hasDrops(ServerWorld world, BlockPos pos, BlockState state) {
		for (ItemStack drop : Block.getDroppedStacks(state, world, pos, null)) {
			if (!drop.isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * @param state {@link BlockState} the state to test
	 * @return {@code true} if the machine is allowed to mine this block at all
	 *         (fluids and unbreakable blocks are never mined)
	 */
	private boolean isMineable(BlockState state) {
		if (state.isAir()) {
			return false;
		}
		if (state.getBlock() == Blocks.BEDROCK || state.getBlock() == Blocks.BARRIER) {
			return false;
		}
		if (!state.getFluidState().isEmpty()) {
			return false;
		}
		return state.getHardness(getWorld(), getPos()) >= 0;
	}

	// =======================================================================
	// mining
	// =======================================================================

	/**
	 * Advances the current target by one tick of breaking progress, harvesting
	 * it when done.
	 *
	 * @return {@code true} if a block was actually harvested this tick
	 */
	private boolean mineStep(ServerWorld world) {
		if (miningPos != null) {
			BlockState state = world.getBlockState(miningPos);
			if (!isMineable(state)) {
				// somebody else removed it, or it is no longer valid
				miningPos = null;
				miningProgress = 0;
			}
		}
		while (miningPos == null && !targets.isEmpty()) {
			BlockPos candidate = targets.remove(0);
			if (isMineable(world.getBlockState(candidate))) {
				miningPos = candidate;
				miningProgress = 0;
			}
		}
		if (miningPos == null) {
			return false;
		}
		if (++miningProgress < breakTimeTicks()) {
			return false;
		}

		// Harvest. Nothing is committed unless every drop fits, so a full
		// output leaves the block in place and the machine reports "not
		// working" (which releases the force-loaded area and stops the draw).
		BlockState state = world.getBlockState(miningPos);
		List<ItemStack> drops = Block.getDroppedStacks(state, world, miningPos, null);
		if (!insertDrops(drops)) {
			return false;
		}
		world.setBlockState(miningPos, Blocks.AIR.getDefaultState(), 3);
		miningPos = null;
		miningProgress = 0;
		return true;
	}

	/**
	 * Inserts drops into the output slots. All-or-nothing: when any drop does
	 * not fit, nothing is inserted so no item is ever lost.
	 *
	 * @param drops {@link List} of {@link ItemStack} produced by the loot table
	 * @return {@code true} if everything was inserted
	 */
	private boolean insertDrops(List<ItemStack> drops) {
		if (drops.isEmpty()) {
			return true;
		}
		List<ItemStack> remaining = new ArrayList<>();
		for (ItemStack drop : drops) {
			if (!drop.isEmpty()) {
				remaining.add(drop.copy());
			}
		}
		if (remaining.isEmpty()) {
			return true;
		}
		// simulate first so a partial insert can never happen
		List<ItemStack> simulated = new ArrayList<>();
		for (ItemStack stack : remaining) {
			simulated.add(stack.copy());
		}
		for (ItemStack stack : simulated) {
			if (!insertSimulated(stack)) {
				return false;
			}
		}
		for (ItemStack stack : remaining) {
			insertIntoOutputs(stack);
		}
		return true;
	}

	private boolean insertSimulated(ItemStack stack) {
		for (int i = 0; i < OUTPUT_SLOTS && !stack.isEmpty(); i++) {
			ItemStack existing = inventory.getStack(i);
			if (existing.isEmpty()) {
				int move = Math.min(stack.getCount(), stack.getMaxCount());
				stack.decrement(move);
			} else if (ItemStack.areItemsAndComponentsEqual(existing, stack)) {
				int space = existing.getMaxCount() - existing.getCount();
				stack.decrement(Math.min(space, stack.getCount()));
			}
		}
		return stack.isEmpty();
	}

	private void insertIntoOutputs(ItemStack stack) {
		for (int i = 0; i < OUTPUT_SLOTS && !stack.isEmpty(); i++) {
			ItemStack existing = inventory.getStack(i);
			if (existing.isEmpty()) {
				int move = Math.min(stack.getCount(), stack.getMaxCount());
				ItemStack placed = stack.copy();
				placed.setCount(move);
				inventory.setStack(i, placed);
				stack.decrement(move);
			} else if (ItemStack.areItemsAndComponentsEqual(existing, stack)) {
				int space = existing.getMaxCount() - existing.getCount();
				int move = Math.min(space, stack.getCount());
				if (move > 0) {
					existing.increment(move);
					stack.decrement(move);
				}
			}
		}
	}

	// =======================================================================
	// chunk force-loading
	// =======================================================================

	/**
	 * Keeps the machine's own chunk force-loaded at all times, and expands to
	 * the whole working area while there is something to do.
	 * <p>
	 * Its own chunk is never released: the block entity has to keep ticking to
	 * notice a repaired structure or newly placed targets, which it could not do
	 * from an unloaded chunk. Only the surrounding {@code radius} chunks are
	 * dropped when the machine goes idle, honouring "stop force-loading once the
	 * outputs are full or nothing is left to mine".
	 *
	 * @param world  {@link ServerWorld} the machine's world
	 * @param active {@code boolean} {@code true} while the machine has work
	 */
	private void updateChunks(ServerWorld world, boolean active) {
		int wanted = active ? radius : 0;
		if (chunksLoaded && loadedRadius == wanted) {
			return;
		}
		// ChunkLoaderManager validates that the owner is non-blank, so a machine
		// whose placer was never recorded (world-generated, an old save, or a
		// controller placed by something other than a player) simply does not
		// force-load instead of crashing every tick.
		if (StringUtils.isBlank(ownerUdid)) {
			return;
		}
		// Drop the previous set first: a shrinking radius would otherwise leave
		// the old, larger ring registered forever.
		unloadChunks();

		ChunkLoaderManager manager = ChunkLoaderManager.get(world);
		ChunkPos root = getChunkPos();
		for (int dx = -wanted; dx <= wanted; dx++) {
			for (int dz = -wanted; dz <= wanted; dz++) {
				manager.loadChunk(world, new ChunkPos(root.x + dx, root.z + dz), getPos(), ownerUdid);
			}
		}
		chunksLoaded = true;
		loadedRadius = wanted;
	}

	/**
	 * Releases every chunk this machine force-loads, including its own. Called
	 * before re-registering a different radius and whenever the machine stops
	 * working entirely (broken, unloaded or invalid structure).
	 */
	private void unloadChunks() {
		if (!chunksLoaded || world == null || world.isClient) {
			chunksLoaded = false;
			loadedRadius = -1;
			return;
		}
		ChunkLoaderManager.get(world).unloadChunkLoader(world, getPos());
		chunksLoaded = false;
		loadedRadius = -1;
	}

	private ChunkPos getChunkPos() {
		return new ChunkPos(getPos());
	}

	// =======================================================================
	// GUI data
	// =======================================================================

	private void refreshDisplay(@Nullable CompiledFilter compiled) {
		displayTargetCount = targets.size() + (miningPos == null ? 0 : 1);
		displaySpeedTicks = breakTimeTicks();
		previewText = buildPreview(compiled);
	}

	/**
	 * Builds the "first N matched blocks" preview shown in the GUI. Walks the
	 * live world rather than the target list so the preview reflects the filter
	 * immediately, even before a scan pass reaches those blocks.
	 *
	 * @param compiled {@link CompiledFilter} the active filter
	 * @return {@link String} newline separated block names, or a status message
	 */
	private String buildPreview(@Nullable CompiledFilter compiled) {
		if (compiled == null) {
			return Text.translatable("gui.techreborn.digital_miner.filter_invalid").getString();
		}
		if (targets.isEmpty()) {
			return scanCompleted
					? Text.translatable("gui.techreborn.digital_miner.no_targets").getString()
					: Text.translatable("gui.techreborn.digital_miner.scanning").getString();
		}
		int limit = Math.max(1, TechRebornConfig.digitalMinerMaxPreviewEntries);
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < targets.size() && i < limit; i++) {
			if (i > 0) {
				builder.append('\n');
			}
			Block block = world.getBlockState(targets.get(i)).getBlock();
			builder.append(block.getName().getString())
					.append(" (")
					.append(targets.get(i).getX()).append(", ")
					.append(targets.get(i).getY()).append(", ")
					.append(targets.get(i).getZ()).append(')');
		}
		if (targets.size() > limit) {
			builder.append('\n').append("... +").append(targets.size() - limit);
		}
		return builder.toString();
	}

	public int getTargetCount() {
		return displayTargetCount;
	}

	public int getSpeedTicks() {
		return displaySpeedTicks;
	}

	public String getPreviewText() {
		return previewText;
	}

	// =======================================================================
	// persistence
	// =======================================================================

	@Override
	public void onPlace(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.onPlace(world, pos, state, placer, stack);
		// recorded here because ChunkLoaderManager needs a non-blank owner
		ownerUdid = placer.getUuidAsString();
	}

	@Override
	public void writeNbt(NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
		super.writeNbt(tag, registryLookup);
		tag.putInt("radius", radius);
		tag.putString("filter", filterText);
		tag.putString("preview", previewText);
		tag.putInt("targets", displayTargetCount);
		tag.putInt("speed", displaySpeedTicks);
		tag.putString("ownerUdid", ownerUdid);
		inventory.write(tag, registryLookup);
	}

	@Override
	public void readNbt(NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
		super.readNbt(tag, registryLookup);
		radius = Math.max(1, Math.min(TechRebornConfig.digitalMinerMaxRadius, tag.getInt("radius")));
		if (tag.contains("filter")) {
			filterText = tag.getString("filter");
		}
		previewText = tag.getString("preview");
		displayTargetCount = tag.getInt("targets");
		displaySpeedTicks = tag.getInt("speed");
		ownerUdid = tag.getString("ownerUdid");
		inventory.read(tag, registryLookup);
		filter = null;
		filterInvalid = false;
	}

	@Override
	public void onBreak(World world, PlayerEntity player, BlockPos pos, BlockState state) {
		unloadChunks();
		super.onBreak(world, player, pos, state);
	}

	/**
	 * Releases the force-loaded chunks. Without this the loader entries would
	 * outlive the machine (and keep its chunks loaded forever) whenever the
	 * controller is removed by something other than a player breaking it, such
	 * as an explosion or a {@code /setblock}.
	 */
	@Override
	public void markRemoved() {
		unloadChunks();
		super.markRemoved();
	}

	// =======================================================================
	// GUI
	// =======================================================================

	@Override
	public BuiltScreenHandler createScreenHandler(int syncID, final PlayerEntity player) {
		BlockEntityScreenHandlerBuilder builder = new ScreenHandlerBuilder("digitalminer")
				.player(player.getInventory()).inventory().hotbar().addInventory()
				.blockEntity(this);
		// 8 outputs in two rows of four
		for (int i = 0; i < OUTPUT_SLOTS; i++) {
			int col = i % 4;
			int row = i / 4;
			builder.outputSlot(i, 53 + col * 18, 26 + row * 18);
		}
		builder.energySlot(ENERGY_SLOT, 8, 72);
		builder.syncEnergyValue();
		builder.sync(PacketCodecs.STRING, this::getFilterText, this::setFilterText);
		builder.sync(PacketCodecs.STRING, this::getPreviewText, this::setPreviewText);
		builder.sync(PacketCodecs.INTEGER, this::getRadius, this::setRadius);
		builder.sync(PacketCodecs.INTEGER, this::getTargetCount, this::setTargetCount);
		builder.sync(PacketCodecs.INTEGER, this::getSpeedTicks, this::setSpeedTicks);
		return builder.addInventory().create(this, syncID);
	}

	public void setPreviewText(String value) {
		previewText = value;
	}

	public void setTargetCount(int value) {
		displayTargetCount = value;
	}

	public void setSpeedTicks(int value) {
		displaySpeedTicks = value;
	}
}
