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

package techreborn.items.tool;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import techreborn.multiblock.IMultiblockStructureMember;
import techreborn.multiblock.MultiblockDefinition;
import techreborn.multiblock.MultiblockDefinitionLoader;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * A tool that builds or inspects JSON-driven multiblock machines.
 * <p>
 * Right-click a machine controller: reports every structure position that is
 * not satisfied yet (absolute world coordinates).
 * <p>
 * Sneak + right-click: queues an automatic build of the missing positions,
 * placing up to 10 blocks per tick from the player's inventory. Missing
 * blocks are reported once per block type in the player's chat. In creative
 * mode the build does not consume any items.
 * <p>
 * In survival mode the builder never overwrites existing blocks: it only fills
 * air. A position blocked by a real block is reported in chat (capped at
 * {@value #MAX_OCCUPIED_MESSAGES} individual lines, then summarised) and
 * re-queued, so clearing the obstruction lets the same job continue. A job
 * that cannot place anything for {@value #MAX_EMPTY_TICKS} consecutive ticks
 * aborts instead of rescanning forever.
 * <p>
 * Interaction is wired through {@code UseBlockCallback} and
 * {@code ServerTickEvents} in {@code TechReborn}, so the machine GUI is not
 * opened while the tool is held.
 */
public class MultiblockBuilderItem extends Item {

	/** One structure position that still needs attention. */
	private record Entry(BlockPos pos, BlockState target, BiPredicate<BlockView, BlockPos> predicate,
			List<Block> candidates) {
	}

	/** A queued build job: the machine's structure to fill, 10 positions/tick. */
	private static final class BuildJob {
		final UUID playerId;
		final RegistryKey<World> worldKey;
		final BlockPos machinePos;
		final ArrayDeque<Entry> queue;
		final Set<Block> reportedMissing = new HashSet<>();
		int placed;
		/** Positions skipped because they were already occupied (survival only). */
		int skippedOccupied;
		/** Whether the "…and N more" overflow line has been sent. */
		boolean overflowReported;
		/** Consecutive ticks that placed nothing; used to abort stuck jobs. */
		int emptyTicks;

		BuildJob(UUID playerId, RegistryKey<World> worldKey, BlockPos machinePos, List<Entry> todo) {
			this.playerId = playerId;
			this.worldKey = worldKey;
			this.machinePos = machinePos;
			this.queue = new ArrayDeque<>(todo);
		}
	}

	private static final Map<UUID, BuildJob> ACTIVE_JOBS = new HashMap<>();
	private static final int BLOCKS_PER_TICK = 10;
	private static final int MAX_REPORTED_POSITIONS = 20;
	/** Cap on "already occupied" chat lines; the total is still reported once. */
	private static final int MAX_OCCUPIED_MESSAGES = 50;
	/** Abort a job after this many consecutive ticks without placing anything. */
	private static final int MAX_EMPTY_TICKS = 40;

	public MultiblockBuilderItem() {
		super(new Item.Settings());
	}

	/**
	 * Handles a right-click on a multiblock machine controller.
	 *
	 * @param player {@link PlayerEntity} the acting player
	 * @param world  {@link World} the world
	 * @param machine {@link IMultiblockStructureMember} the clicked machine
	 * @return {@code ActionResult.SUCCESS} when handled (also on the client, to
	 *         prevent the machine GUI from opening)
	 */
	public static void handleUse(PlayerEntity player, World world, IMultiblockStructureMember machine) {
		if (player.isSneaking()) {
			startBuild(player, world, machine);
		} else {
			reportMissing(player, world, machine);
		}
	}

	/**
	 * Queues an automatic build of every unsatisfied structure position.
	 * Positions whose predicate is already satisfied are skipped.
	 */
	private static void startBuild(PlayerEntity player, World world, IMultiblockStructureMember machine) {
		List<Entry> todo = new ArrayList<>();
		for (Entry entry : collectStructure(world, machine)) {
			if (!entry.predicate().test(world, entry.pos())) {
				todo.add(entry);
			}
		}
		if (todo.isEmpty()) {
			player.sendMessage(Text.literal("结构已经完整，无需搭建！"));
			return;
		}
		ACTIVE_JOBS.put(player.getUuid(),
				new BuildJob(player.getUuid(), world.getRegistryKey(), machine.getPos(), todo));
		player.sendMessage(Text.literal("开始搭建 " + machineName(world, machine) + "，共 " + todo.size()
				+ " 个位置需要处理"));
	}

	/**
	 * Reports every unsatisfied structure position with its absolute world
	 * coordinates. The list is capped to avoid chat spam on huge structures.
	 */
	private static void reportMissing(PlayerEntity player, World world, IMultiblockStructureMember machine) {
		List<Entry> missing = new ArrayList<>();
		for (Entry entry : collectStructure(world, machine)) {
			if (!entry.predicate().test(world, entry.pos())) {
				missing.add(entry);
			}
		}
		if (missing.isEmpty()) {
			player.sendMessage(Text.literal(machineName(world, machine) + "：§a结构已成型！§r"));
			return;
		}
		player.sendMessage(Text.literal(machineName(world, machine) + "：§c结构不完整§r，缺少 " + missing.size()
				+ " 个位置"));
		for (Entry entry : missing.subList(0, Math.min(missing.size(), MAX_REPORTED_POSITIONS))) {
			BlockPos pos = entry.pos();
			player.sendMessage(Text.literal(" §b (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")§r - 需要: §a"
					+ blockName(entry.target())));
		}
		if (missing.size() > MAX_REPORTED_POSITIONS) {
			player.sendMessage(Text.literal("  ... 其余 " + (missing.size() - MAX_REPORTED_POSITIONS) + " 个位置省略"));
		}
	}

	/**
	 * Advances every queued build job by up to 10 positions. Called from
	 * {@code ServerTickEvents.END_SERVER_TICK}.
	 *
	 * @param server {@link MinecraftServer} the running server
	 */
	public static void tickJobs(MinecraftServer server) {
		if (ACTIVE_JOBS.isEmpty()) {
			return;
		}
		ACTIVE_JOBS.entrySet().removeIf(entry -> finishTick(server, entry.getValue()));
	}

	private static boolean finishTick(MinecraftServer server, BuildJob job) {
		ServerWorld world = server.getWorld(job.worldKey);
		if (world == null) {
			return true;
		}
		ServerPlayerEntity player = server.getPlayerManager().getPlayer(job.playerId);
		if (player == null || !player.isAlive()) {
			return true;
		}
		if (!(world.getBlockEntity(job.machinePos) instanceof IMultiblockStructureMember)) {
			return true;
		}

		int budget = BLOCKS_PER_TICK;
		int placedBefore = job.placed;
		while (budget > 0 && !job.queue.isEmpty()) {
			Entry entry = job.queue.poll();
			tryPlace(world, player, entry, job);
			budget--;
		}

		// Positions may be permanently occupied (survival mode never overwrites
		// blocks), in which case the queue can never drain. Stop instead of
		// re-scanning it forever.
		if (job.placed == placedBefore) {
			if (++job.emptyTicks >= MAX_EMPTY_TICKS) {
				player.sendMessage(Text.literal("§e搭建已中止§r：有位置被其它方块占用，无法继续"));
				return true;
			}
		} else {
			job.emptyTicks = 0;
		}

		if (job.queue.isEmpty()) {
			if (job.skippedOccupied > 0) {
				player.sendMessage(Text.literal("§e搭建结束！§r共放置 §a" + job.placed
						+ "§r 个方块，§c" + job.skippedOccupied + "§r 个位置被其它方块占用、已跳过"));
			} else {
				player.sendMessage(Text.literal("§e搭建完成！§r共放置 §a" + job.placed + "§r 个方块"));
			}
			return true;
		}
		return false;
	}

	private static void tryPlace(ServerWorld world, ServerPlayerEntity player, Entry entry, BuildJob job) {
		// Already satisfied (e.g. another source filled it): skip silently.
		if (entry.predicate().test(world, entry.pos())) {
			return;
		}
		// In survival the builder never overwrites existing blocks: it only
		// fills air. Positions blocked by a real block are reported and
		// re-queued so the player can clear them and let the build finish.
		if (!player.isCreative() && !world.isAir(entry.pos())) {
			reportOccupied(player, entry.pos(), job);
			job.queue.add(entry);
			return;
		}
		Block targetBlock = entry.target().getBlock();
		// Air targets (structure holes / "not X" keys) never need items.
		if (targetBlock == Blocks.AIR) {
			world.setBlockState(entry.pos(), Blocks.AIR.getDefaultState(), 3);
			job.placed++;
			return;
		}
		if (player.isCreative()) {
			world.setBlockState(entry.pos(), entry.target(), 3);
			job.placed++;
			return;
		}
		// Keys may accept several blocks (e.g. any coil type in the EBF): try
		// every candidate from the inventory, not just the first one shown.
		List<Block> candidates = entry.candidates().isEmpty()
				? List.of(targetBlock)
				: entry.candidates();
		Block placedBlock = consumeAnyFromInventory(player, candidates);
		if (placedBlock == null) {
			// Report each missing block type once per job to avoid spam.
			if (job.reportedMissing.add(targetBlock)) {
				player.sendMessage(Text.literal("§c缺少 " + blockName(entry.target()) + "，跳过该位置§r"));
			}
			return;
		}
		world.setBlockState(entry.pos(), placedBlock.getDefaultState(), 3);
		job.placed++;
	}

	/**
	 * Reports a position that could not be filled because a block already
	 * occupies it. Individual lines are capped at
	 * {@value #MAX_OCCUPIED_MESSAGES}; the remaining positions are summarised
	 * in a single line instead of flooding the chat.
	 *
	 * @param player {@link ServerPlayerEntity} the acting player
	 * @param pos    {@link BlockPos} the occupied position
	 * @param job    {@link BuildJob} the job, used for counting and de-duplication
	 */
	private static void reportOccupied(ServerPlayerEntity player, BlockPos pos, BuildJob job) {
		job.skippedOccupied++;
		if (job.skippedOccupied <= MAX_OCCUPIED_MESSAGES) {
			player.sendMessage(Text.literal("§c位置 §b(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()
					+ ")§c 已被方块占用，生存模式下不覆盖§r"));
		} else if (!job.overflowReported) {
			job.overflowReported = true;
			player.sendMessage(Text.literal("§e…仍有更多位置被占用，后续不再逐条提示（已达 "
					+ MAX_OCCUPIED_MESSAGES + " 条上限）§r"));
		}
	}

	/**
	 * Finds the first candidate block the player has in their inventory and
	 * consumes one item of it.
	 *
	 * @param player     {@link PlayerEntity} the player
	 * @param candidates {@link List} candidate blocks to look for
	 * @return the consumed block, or {@code null} if none is available
	 */
	private static Block consumeAnyFromInventory(PlayerEntity player, List<Block> candidates) {
		for (int i = 0; i < player.getInventory().size(); i++) {
			ItemStack stack = player.getInventory().getStack(i);
			if (stack.isEmpty()) {
				continue;
			}
			for (Block candidate : candidates) {
				if (stack.isOf(candidate.asItem())) {
					player.getInventory().removeStack(i, 1);
					return candidate;
				}
			}
		}
		return null;
	}

	/**
	 * Collects every structure position as a world-space {@link Entry} with the
	 * full key definition (all candidate blocks), applying the same rotation as
	 * validation.
	 */
	private static List<Entry> collectStructure(World world, IMultiblockStructureMember machine) {
		List<Entry> entries = new ArrayList<>();
		MultiblockDefinition definition = MultiblockDefinitionLoader.get(machine.getMultiblockId());
		if (definition == null) {
			return entries;
		}
		definition.forEachKey(machine.getPos(), machine.getFacing().getOpposite(), (pos, key) ->
				entries.add(new Entry(pos, key.getHologramState(), key.getPredicate(), key.getCandidateBlocks())));
		return entries;
	}

	private static String machineName(World world, IMultiblockStructureMember machine) {
		return world.getBlockState(machine.getPos()).getBlock().getName().getString();
	}

	private static String blockName(BlockState state) {
		return state.getBlock().getName().getString();
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
		super.appendTooltip(stack, context, tooltip, type);
		tooltip.add(Text.translatable("item.techreborn.multiblock_builder.tooltip.0"));
		tooltip.add(Text.translatable("item.techreborn.multiblock_builder.tooltip.1"));
		tooltip.add(Text.translatable("item.techreborn.multiblock_builder.tooltip.2"));
		tooltip.add(Text.translatable("item.techreborn.multiblock_builder.tooltip.3"));
		tooltip.add(Text.translatable("item.techreborn.multiblock_builder.tooltip.4"));
	}
}
