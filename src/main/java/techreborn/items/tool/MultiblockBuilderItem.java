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
import techreborn.blockentity.machine.multiblock.JsonMultiblockMachineBlockEntity;
import reborncore.common.blockentity.MultiblockWriter;

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
 * Interaction is wired through {@code UseBlockCallback} and
 * {@code ServerTickEvents} in {@code TechReborn}, so the machine GUI is not
 * opened while the tool is held.
 */
public class MultiblockBuilderItem extends Item {

	/** One structure position that still needs attention. */
	private record Entry(BlockPos pos, BlockState target, BiPredicate<BlockView, BlockPos> predicate) {
	}

	/** A queued build job: the machine's structure to fill, 10 positions/tick. */
	private static final class BuildJob {
		final UUID playerId;
		final RegistryKey<World> worldKey;
		final BlockPos machinePos;
		final ArrayDeque<Entry> queue;
		final Set<Block> reportedMissing = new HashSet<>();
		int placed;

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

	public MultiblockBuilderItem() {
		super(new Item.Settings());
	}

	/**
	 * Handles a right-click on a multiblock machine controller.
	 *
	 * @param player {@link PlayerEntity} the acting player
	 * @param world  {@link World} the world
	 * @param machine {@link JsonMultiblockMachineBlockEntity} the clicked machine
	 * @return {@code ActionResult.SUCCESS} when handled (also on the client, to
	 *         prevent the machine GUI from opening)
	 */
	public static void handleUse(PlayerEntity player, World world, JsonMultiblockMachineBlockEntity machine) {
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
	private static void startBuild(PlayerEntity player, World world, JsonMultiblockMachineBlockEntity machine) {
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
	private static void reportMissing(PlayerEntity player, World world, JsonMultiblockMachineBlockEntity machine) {
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
		if (!(world.getBlockEntity(job.machinePos) instanceof JsonMultiblockMachineBlockEntity)) {
			return true;
		}

		int budget = BLOCKS_PER_TICK;
		while (budget > 0 && !job.queue.isEmpty()) {
			Entry entry = job.queue.poll();
			tryPlace(world, player, entry, job);
			budget--;
		}

		if (job.queue.isEmpty()) {
			player.sendMessage(Text.literal("§e搭建完成！§r共放置 §a" + job.placed + "§r 个方块"));
			return true;
		}
		return false;
	}

	private static void tryPlace(ServerWorld world, ServerPlayerEntity player, Entry entry, BuildJob job) {
		// Already satisfied (e.g. another source filled it): skip silently.
		if (entry.predicate().test(world, entry.pos())) {
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
		if (!consumeFromInventory(player, targetBlock.asItem())) {
			// Report each missing block type once per job to avoid spam.
			if (job.reportedMissing.add(targetBlock)) {
				player.sendMessage(Text.literal("§c缺少 " + blockName(entry.target()) + "，跳过该位置§r"));
			}
			return;
		}
		world.setBlockState(entry.pos(), entry.target(), 3);
		job.placed++;
	}

	private static boolean consumeFromInventory(PlayerEntity player, Item item) {
		for (int i = 0; i < player.getInventory().size(); i++) {
			ItemStack stack = player.getInventory().getStack(i);
			if (stack.isOf(item)) {
				player.getInventory().removeStack(i, 1);
				return true;
			}
		}
		return false;
	}

	/**
	 * Collects every structure position as a world-space {@link Entry} by
	 * writing the machine's definition through a collecting
	 * {@link MultiblockWriter}, applying the same rotation as validation.
	 */
	private static List<Entry> collectStructure(World world, JsonMultiblockMachineBlockEntity machine) {
		List<Entry> entries = new ArrayList<>();
		MultiblockWriter collector = new MultiblockWriter() {
			@Override
			public MultiblockWriter add(int x, int y, int z, BiPredicate<BlockView, BlockPos> predicate,
					BlockState state) {
				entries.add(new Entry(machine.getPos().add(x, y, z), state, predicate));
				return this;
			}
		};
		machine.writeMultiblock(collector.rotate(machine.getFacing().getOpposite()));
		return entries;
	}

	private static String machineName(World world, JsonMultiblockMachineBlockEntity machine) {
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
	}
}
