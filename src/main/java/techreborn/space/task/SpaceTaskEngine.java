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

package techreborn.space.task;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import reborncore.common.blockentity.RedstoneConfiguration;
import reborncore.common.blocks.BlockMachineBase;
import techreborn.TechReborn;
import techreborn.blockentity.machine.multiblock.JsonMultiblockMachineBlockEntity;
import techreborn.blockentity.machine.multiblock.SpaceElevatorBlockEntity;
import techreborn.blockentity.machine.multiblock.SpaceElevatorMinerBlockEntity;
import techreborn.multiblock.CoilHeatScanner;
import techreborn.space.SpaceMachineState;
import techreborn.space.SpaceProgressState;
import techreborn.space.SpaceTerminalService;
import techreborn.space.data.PlanetCatalog;
import techreborn.space.data.PlanetDefinition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-thread task driver. Inventories, escrow and pending delivery are saved together. */
public final class SpaceTaskEngine {
	private static final Map<SpaceTaskIndex.Location, JsonMultiblockMachineBlockEntity> MACHINES = new HashMap<>();
	private static final Map<SpaceTaskIndex.Location, Set<Long>> TICKETS = new HashMap<>();
	private static final ChunkTicketType<BlockPos> TICKET = ChunkTicketType.create("techreborn:space_task", Comparator.comparingLong(BlockPos::asLong));
	private static final int[] HOST_INPUT = {0,1,2,3,4,5,6,7,8};
	private static final int[] HOST_OUTPUT = {9,10,11,12,13,14,15,16,17};
	private static final int[] MINER_INPUT = {0,1,2,3};
	private static final int[] MINER_OUTPUT = {4,5,6,7,8,9};
	private static MinecraftServer activeServer;
	private static int rotation;

	private SpaceTaskEngine() { }

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			activeServer = server;
			SpaceTaskIndex.get(server).beginCheckpoint();
			server.getOverworld().getPersistentStateManager().save();
			// Restore persisted running locations before the first world tick, even
			// when the task owner is offline and no player loads the controller.
			for (var entry : SpaceTaskIndex.get(server).entries()) {
				ServerWorld world = world(server, entry.location());
				if (world == null) { continue; }
				if (entry.forceLoad()) { tickets(world, entry.location(), new HashSet<>(entry.chunks())); }
				world.getChunk(entry.location().pos());
				if (world.getBlockEntity(entry.location().pos()) instanceof JsonMultiblockMachineBlockEntity machine && container(machine) != null) {
					register(machine);
					if (container(machine).task() == null && container(machine).completed().isEmpty()) {
						SpaceTaskIndex.get(server).remove(entry.location());
					}
				} else {
					release(server, entry.location());
					SpaceTaskIndex.get(server).remove(entry.location());
				}
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(SpaceTaskEngine::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			for (var location : List.copyOf(TICKETS.keySet())) { release(server, location); }
			MACHINES.clear(); TICKETS.clear(); activeServer = null;
		});
	}

	public static SpaceTaskContainer container(JsonMultiblockMachineBlockEntity machine) {
		if (machine instanceof SpaceElevatorBlockEntity host) { return host.getTaskContainer(); }
		if (machine instanceof SpaceElevatorMinerBlockEntity miner) { return miner.getTaskContainer(); }
		return null;
	}

	private static SpaceTaskIndex.Location location(JsonMultiblockMachineBlockEntity machine) {
		return new SpaceTaskIndex.Location(machine.getWorld().getRegistryKey().getValue(), machine.getPos());
	}

	public static void register(JsonMultiblockMachineBlockEntity machine) {
		if (!(machine.getWorld() instanceof ServerWorld world) || container(machine) == null) { return; }
		MACHINES.put(location(machine), machine);
		SpaceTaskContainer state = container(machine);
		UUID owner = SpaceTerminalService.state(machine).ownerScope();
		if (state.task() != null && (owner == null || SpaceTerminalService.state(machine).ownerLocked()
				|| !owner.equals(state.task().owner()))) {
			state.fault("Task frozen owner differs from machine owner");
			release(world.getServer(), location(machine));
			return;
		}
		if (owner != null && !state.completed().isEmpty()) {
			state.completed().forEach((body, stage) -> SpaceProgressState.advance(world, owner, body, stage));
			world.getServer().getOverworld().getPersistentStateManager().save();
			if (state.completionEpoch() < SpaceTaskIndex.get(world.getServer()).checkpoint()) { state.clearCompletions(); }
		}
		if (state.task() != null) {
			state.status(switch (state.task().phase()) {
				case RUNNING -> state.task().userPaused() ? "paused_user" : "running";
				case WAITING_OUTPUT -> "waiting_output";
				case REFUNDING -> "refunding";
			}, "");
		}
		long epoch = SpaceTaskIndex.get(world.getServer()).epoch();
		if (state.epoch() < epoch) { reloadMachine(machine, epoch); }
		reconcile(machine);
	}

	/** Chunk unloading does not destroy tasks or their persisted locators. */
	public static void unregister(JsonMultiblockMachineBlockEntity machine) {
		if (machine.getWorld() instanceof ServerWorld) { MACHINES.remove(location(machine), machine); }
	}

	public static long revision(JsonMultiblockMachineBlockEntity machine) {
		SpaceTaskContainer state = container(machine);
		return state == null ? 0 : state.revision();
	}

	public static String start(ServerPlayerEntity player, JsonMultiblockMachineBlockEntity machine) {
		if (!authorized(player, machine)) { return "no_permission"; }
		if (!(machine instanceof SpaceElevatorBlockEntity)) { return "invalid_selection"; }
		register(machine);
		String result = launch(machine);
		container(machine).status(container(machine).task() == null ? "idle" : container(machine).status(), result);
		return result;
	}

	public static String toggleAuto(ServerPlayerEntity player, JsonMultiblockMachineBlockEntity machine) {
		if (!authorized(player, machine) || !(machine instanceof SpaceElevatorMinerBlockEntity)) { return "no_permission"; }
		register(machine);
		SpaceTaskContainer state = container(machine);
		if (state.invalid()) { return "invalid_task"; }
		if (state.autoEnabled()) {
			state.autoEnabled(false); // Current snapshot finishes; only future rounds stop.
			return "";
		}
		String reason = prerequisites(machine);
		if (!reason.isEmpty()) { return reason; }
		state.autoEnabled(true);
		if (state.task() == null) { state.status("idle", launch(machine)); }
		return "";
	}

	public static String togglePause(ServerPlayerEntity player, JsonMultiblockMachineBlockEntity machine) {
		if (!authorized(player, machine)) { return "no_permission"; }
		SpaceTaskContainer state = container(machine);
		if (state.invalid()) { return "invalid_task"; }
		if (state.task() == null || state.task().phase() != SpaceTask.Phase.RUNNING) { return "task_busy"; }
		state.task().setUserPaused(!state.task().userPaused());
		state.changed();
		state.status(state.task().userPaused() ? "paused_user" : "running", "");
		reconcile(machine);
		return "";
	}

	private static boolean authorized(ServerPlayerEntity player, JsonMultiblockMachineBlockEntity machine) {
		return player != null && machine != null && container(machine) != null
				&& machine.getWorld() == player.getServerWorld() && machine.getWorld().getBlockEntity(machine.getPos()) == machine
				&& player.squaredDistanceTo(machine.getPos().toCenterPos()) <= 256
				&& SpaceTerminalService.state(machine).canConfigure(player) && player.getServer().isOnThread();
	}

	private static String prerequisites(JsonMultiblockMachineBlockEntity machine) {
		SpaceTaskContainer state = container(machine);
		if (state.invalid()) { return "invalid_task"; }
		if (machine.getWorld().getRegistryKey() != World.OVERWORLD) { return "host_unavailable"; }
		if (!machine.isMultiblockValid()) { return "structure_missing"; }
		if (!machine.isActive(RedstoneConfiguration.Element.RECIPE_PROCESSING)) { return "paused_redstone"; }
		if (machine instanceof SpaceElevatorMinerBlockEntity miner && miner.getAvailableTaskHost() == null) { return "host_unavailable"; }
		SpaceMachineState selection = SpaceTerminalService.state(machine);
		if (selection.ownerScope() == null || selection.ownerLocked()) { return "no_permission"; }
		PlanetDefinition body = selection.selectedBody() == null ? null : PlanetCatalog.get(selection.selectedBody());
		if (body == null || body.type().equals("star")) { return "invalid_selection"; }
		int stage = SpaceProgressState.get(machine.getWorld()).stage(selection.ownerScope(), body.id());
		if (machine instanceof SpaceElevatorMinerBlockEntity) {
			if (!body.missions().mining().enabled()) { return "invalid_selection"; }
			if (stage < 1) { return "survey_required"; }
			if (body.missions().mining().requiresLanding() && stage < 2) { return "landing_required"; }
			if (!SpaceTerminalService.visible(body.visibility().resources(), stage) || selection.selectedResources().isEmpty()
					|| selection.selectedResources().size() > 3 || new HashSet<>(selection.selectedResources()).size() != selection.selectedResources().size()
					|| selection.selectedResources().stream().anyMatch(id -> body.resources().stream().noneMatch(r -> r.id().equals(id)))) {
				return "invalid_selection";
			}
		} else if (stage >= 3 || body.type().equals("black_hole") && stage >= 1) { return "completed_stage"; }
		if (SpaceTerminalService.variants(body, stage, machine instanceof SpaceElevatorMinerBlockEntity).stream()
				.noneMatch(v -> v.tier() == selection.selectedVariantTier())) { return "scheme_unavailable"; }
		return "";
	}

	private static String launch(JsonMultiblockMachineBlockEntity machine) {
		SpaceTaskContainer state = container(machine);
		if (state.task() != null) { return state.task().phase() == SpaceTask.Phase.REFUNDING ? "refunding" : "task_busy"; }
		String reason = prerequisites(machine);
		if (!reason.isEmpty()) { return reason; }
		SpaceMachineState selection = SpaceTerminalService.state(machine);
		boolean miner = machine instanceof SpaceElevatorMinerBlockEntity;
		var world = (ServerWorld) machine.getWorld();
		PlanetDefinition body = PlanetCatalog.get(selection.selectedBody());
		int stage = SpaceProgressState.get(world).stage(selection.ownerScope(), body.id());
		if (!miner && SpaceTaskIndex.get(world.getServer()).busy(selection.ownerScope(), body.id(), location(machine))) { return "body_busy"; }
		var variant = SpaceTerminalService.variants(body, stage, miner).stream().filter(v -> v.tier() == selection.selectedVariantTier()).findFirst().orElseThrow();
		List<ItemStack> outputs = new ArrayList<>(variant.outputs());
		if (miner) {
			boolean outpost = stage >= 3 && !body.type().equals("black_hole");
			for (var resource : body.resources()) {
				if (!selection.selectedResources().contains(resource.id())) { continue; }
				ItemStack stack = resource.stack();
				int count = outpost ? stack.getCount() : stack.getCount() / 5;
				if (count > 0) { outputs.add(stack.copyWithCount(count)); }
			}
		}
		Inventory inventory = machine.getInventory();
		if (!SpaceTaskInventory.fitsEmptyOutputs(inventory, outputSlots(machine), outputs)) { return "output_capacity"; }
		if (!SpaceTaskInventory.canFit(inventory, outputSlots(machine), outputs)) { return "output_full"; }
		// Validate ticket bounds before touching inputs; no launch-time exception
		// may strand consumed items outside a task container.
		taskChunks(machine);
		List<ItemStack> escrow = SpaceTaskInventory.consume(inventory, inputSlots(machine), variant.inputs());
		if (escrow == null) { return "missing_inputs"; }
		SpaceTask.Kind kind = miner ? SpaceTask.Kind.MINING : switch (stage) {
			case 0 -> SpaceTask.Kind.SURVEY;
			case 1 -> SpaceTask.Kind.LAND;
			case 2 -> SpaceTask.Kind.OUTPOST;
			default -> throw new IllegalStateException("Invalid exploration stage");
		};
		state.task(new SpaceTask(selection.ownerScope(), body.id(), kind, variant.tier(), SpaceTerminalService.power(variant.tier()),
				SpaceTerminalService.missionTime(body, stage, miner, variant.tier()), escrow, outputs));
		state.epoch(SpaceTaskIndex.get(world.getServer()).epoch());
		state.status("running", "");
		reconcile(machine);
		world.getServer().getOverworld().getPersistentStateManager().save();
		SpaceTaskFeedback.transition(machine, state.task(), "launch");
		return "";
	}

	private static void tick(MinecraftServer server) {
		List<JsonMultiblockMachineBlockEntity> machines = new ArrayList<>(MACHINES.values());
		machines.sort(Comparator.comparingLong(machine -> machine.getPos().asLong()));
		if (!machines.isEmpty()) { rotation = Math.floorMod(rotation + 1, machines.size()); }
		for (int i = 0; i < machines.size(); i++) {
			var machine = machines.get((i + rotation) % machines.size());
			if (!(machine.getWorld() instanceof ServerWorld world) || machine.isRemoved()
					|| !world.isChunkLoaded(machine.getPos()) || world.getBlockEntity(machine.getPos()) != machine) {
				MACHINES.remove(location(machine), machine); continue;
			}
			try { tickMachine(machine, server.getTicks()); }
			catch (RuntimeException exception) {
				TechReborn.LOGGER.error("Space task execution paused at {}: {}", machine.getPos(), exception.toString());
				container(machine).fault(exception.toString());
				release(server, location(machine));
			}
		}
	}

	private static void tickMachine(JsonMultiblockMachineBlockEntity machine, int tick) {
		SpaceTaskContainer state = container(machine);
		SpaceTask task = state.task();
		if (state.invalid()) { state.status("invalid_task", "invalid_task"); return; }
		if (task == null) {
			if (state.autoEnabled() && (state.reason().isEmpty() || tick % 20 == 0)) {
				String reason = launch(machine);
				state.status(state.task() == null ? "idle" : "running", reason);
				task = state.task();
			}
			if (task == null) { return; }
		}
		if (task.phase() != SpaceTask.Phase.RUNNING) {
			if (task.phase() == SpaceTask.Phase.WAITING_OUTPUT) { award(machine, task); }
			List<ItemStack> rest = SpaceTaskInventory.insert(machine.getInventory(), task.phase() == SpaceTask.Phase.REFUNDING ? inputSlots(machine) : outputSlots(machine), task.pending());
			task.replacePending(rest); state.changed();
			if (rest.isEmpty()) { state.task(null); state.status("idle", ""); }
			else { state.status(task.phase() == SpaceTask.Phase.REFUNDING ? "refunding" : "waiting_output", task.phase() == SpaceTask.Phase.REFUNDING ? "refunding" : "output_full"); }
			reconcile(machine); setActive(machine, false); return;
		}
		String pause = task.userPaused() ? "paused_user"
				: !machine.isMultiblockValid() ? "paused_structure"
				: !machine.isActive(RedstoneConfiguration.Element.RECIPE_PROCESSING) ? "paused_redstone"
				: machine instanceof SpaceElevatorMinerBlockEntity miner && miner.getAvailableTaskHost() == null ? "paused_host"
				: machine.getEnergy() < task.power() ? "paused_power" : "";
		if (!pause.isEmpty()) { state.status(pause, pause); setActive(machine, false); reconcile(machine); return; }
		machine.useEnergy(task.power());
		state.consumed(task.power(), tick);
		boolean finished = task.tickProgress(); state.changed();
		state.status(finished ? "waiting_output" : "running", "");
		if (finished) {
			award(machine, task);
			SpaceTaskFeedback.transition(machine, task, "complete");
		} else { SpaceTaskFeedback.running(machine, task); }
		setActive(machine, !finished); reconcile(machine);
		if (finished) {
			// Deliver in the completion tick, then permit the next paid round on
			// the next tick. Failed starts retry at 1 Hz without slowing live rounds.
			tickMachine(machine, tick);
		}
	}

	private static void award(JsonMultiblockMachineBlockEntity machine, SpaceTask task) {
		int stage = switch (task.kind()) { case SURVEY -> 1; case LAND -> 2; case OUTPOST -> 3; case MINING -> 0; };
		if (stage > 0) {
			ServerWorld world = (ServerWorld) machine.getWorld();
			boolean changed = SpaceProgressState.advance(world, task.owner(), task.body(), stage);
			if (changed || container(machine).completed().getOrDefault(task.body(), 0) < stage) {
				container(machine).acknowledgeCompletion(task.body(), stage, SpaceTaskIndex.get(world.getServer()).checkpoint());
				// Stage durability precedes removal of the same-chunk completion receipt.
				world.getServer().getOverworld().getPersistentStateManager().save();
			}
		}
	}

	private static void setActive(JsonMultiblockMachineBlockEntity machine, boolean active) {
		// Host active/bloom means structural availability for bound assemblers;
		// its regular tick owns that state. Miner active means real paid progress.
		if (machine instanceof SpaceElevatorMinerBlockEntity && machine.isActive() != active) {
			BlockState state = machine.getCachedState();
			if (state.getBlock() instanceof BlockMachineBase block) { block.setActive(active, machine.getWorld(), machine.getPos()); }
		}
	}

	public static void beforeReload(MinecraftServer server) {
		SpaceTaskIndex index = SpaceTaskIndex.get(server);
		index.nextEpoch(); // Also catches manually paused/unloaded tasks when next opened.
		for (var entry : index.entries()) {
			ServerWorld world = world(server, entry.location());
			if (world == null) { continue; }
			world.getChunk(entry.location().pos());
			if (world.getBlockEntity(entry.location().pos()) instanceof JsonMultiblockMachineBlockEntity machine && container(machine) != null) {
				register(machine);
			}
		}
		for (var machine : List.copyOf(MACHINES.values())) { reloadMachine(machine, index.epoch()); }
		server.getOverworld().getPersistentStateManager().save();
	}

	private static void reloadMachine(JsonMultiblockMachineBlockEntity machine, long epoch) {
		SpaceTaskContainer state = container(machine);
		state.autoEnabled(false);
		if (state.task() != null && state.task().phase() == SpaceTask.Phase.RUNNING) {
			SpaceTaskFeedback.transition(machine, state.task(), "refund");
			state.task().refund(); state.changed(); state.status("refunding", "refunding");
		}
		state.epoch(epoch);
		reconcile(machine);
	}

	/** A wrench preserves completed/refunding inventory, but destroys unfinished investment. */
	public static void dismantle(JsonMultiblockMachineBlockEntity machine) {
		if (!(machine.getWorld() instanceof ServerWorld world)) { return; }
		SpaceTaskContainer state = container(machine);
		if (state.task() != null && state.task().phase() == SpaceTask.Phase.RUNNING) { state.task(null); }
		state.autoEnabled(false);
		release(world.getServer(), location(machine));
		SpaceTaskIndex.get(world.getServer()).remove(location(machine));
		MACHINES.remove(location(machine), machine);
	}

	/** Called before normal contained-item drops, including explosion removal. */
	public static void removedWithDrops(JsonMultiblockMachineBlockEntity machine) {
		if (!(machine.getWorld() instanceof ServerWorld world)) { return; }
		SpaceTaskContainer state = container(machine);
		SpaceTask task = state.task();
		if (task != null && task.phase() != SpaceTask.Phase.RUNNING) {
			if (task.phase() == SpaceTask.Phase.WAITING_OUTPUT) { award(machine, task); }
			// Completed/refunded pending goods are owned inventory, not unfinished investment.
			List<ItemStack> pending = task.pending();
			state.task(null);
			for (ItemStack stack : pending) {
				int remaining = stack.getCount();
				while (remaining > 0) {
					int count = Math.min(remaining, Math.max(1, stack.getMaxCount()));
					ItemScatterer.spawn(world, machine.getPos().getX(), machine.getPos().getY(), machine.getPos().getZ(), stack.copyWithCount(count));
					remaining -= count;
				}
			}
		} else if (task != null) { state.task(null); } // Unfinished investment is deliberately lost.
		state.autoEnabled(false);
		release(world.getServer(), location(machine)); SpaceTaskIndex.get(world.getServer()).remove(location(machine));
		MACHINES.remove(location(machine), machine);
	}

	public static NbtCompound view(JsonMultiblockMachineBlockEntity machine, ServerPlayerEntity viewer) {
		NbtCompound view = new NbtCompound();
		if (!SpaceTerminalService.state(machine).canConfigure(viewer)) { return view; }
		SpaceTaskContainer state = container(machine);
		SpaceTask task = state.task();
		view.putString("taskState", state.status()); view.putString("reason", state.reason());
		view.putBoolean("autoEnabled", state.autoEnabled());
		state.writeVisual(view);
		if (task != null) {
			view.putString("taskKind", task.kind().name().toLowerCase(java.util.Locale.ROOT));
			view.putLong("taskProgress", task.progress()); view.putLong("taskDuration", task.duration());
			view.putLong("taskPower", task.power()); view.putString("taskBody", task.body().toString());
			view.putBoolean("userPaused", task.userPaused()); view.putString("taskPhase", task.phase().name());
		}
		return view;
	}

	private static int[] inputSlots(JsonMultiblockMachineBlockEntity machine) { return machine instanceof SpaceElevatorBlockEntity ? HOST_INPUT : MINER_INPUT; }
	private static int[] outputSlots(JsonMultiblockMachineBlockEntity machine) { return machine instanceof SpaceElevatorBlockEntity ? HOST_OUTPUT : MINER_OUTPUT; }

	private static void reconcile(JsonMultiblockMachineBlockEntity machine) {
		if (!(machine.getWorld() instanceof ServerWorld world)) { return; }
		SpaceTaskContainer state = container(machine);
		SpaceTask task = state.task();
		var location = location(machine);
		if (task == null || state.invalid()) {
			release(world.getServer(), location);
			// Keep a completed locator until a later startup has recovered the
			// machine chunk. A crash may have saved the index before the chunk.
			if (state.invalid() || state.completed().isEmpty()) {
				var old = SpaceTaskIndex.get(world.getServer()).get(location);
				if (old != null && !state.invalid()) {
					SpaceTaskIndex.get(world.getServer()).put(new SpaceTaskIndex.Entry(location, old.taskId(), old.owner(), old.body(),
							false, true, false, List.of()));
				}
			} else {
				var receipt = state.completed().entrySet().iterator().next();
				SpaceTaskIndex.get(world.getServer()).put(new SpaceTaskIndex.Entry(location,
						new UUID(0, 0), SpaceTerminalService.state(machine).ownerScope(), receipt.getKey(), false, true, false, List.of()));
			}
			return;
		}
		boolean force = task.phase() == SpaceTask.Phase.RUNNING && !task.userPaused();
		Set<Long> chunks = force && TICKETS.containsKey(location) ? TICKETS.get(location) : force ? taskChunks(machine) : Set.of();
		if (force) { tickets(world, location, chunks); world.resetIdleTimeout(); }
		else { release(world.getServer(), location); }
		SpaceTaskIndex.get(world.getServer()).put(new SpaceTaskIndex.Entry(location, task.id(), task.owner(), task.body(),
				task.kind() != SpaceTask.Kind.MINING, task.phase() != SpaceTask.Phase.RUNNING, force, List.copyOf(chunks)));
	}

	private static Set<Long> taskChunks(JsonMultiblockMachineBlockEntity machine) {
		Set<Long> chunks = new LinkedHashSet<>(); addStructureChunks(machine, chunks);
		if (machine instanceof SpaceElevatorMinerBlockEntity miner && miner.getHostPos() != null) {
			chunks.add(new ChunkPos(miner.getHostPos()).toLong());
			if (machine.getWorld().getBlockEntity(miner.getHostPos()) instanceof SpaceElevatorBlockEntity host) { addStructureChunks(host, chunks); }
		}
		if (chunks.size() > 256) { throw new IllegalArgumentException("Space task structure spans more than 256 chunks"); }
		return chunks;
	}
	private static void addStructureChunks(JsonMultiblockMachineBlockEntity machine, Set<Long> chunks) {
		chunks.add(new ChunkPos(machine.getPos()).toLong());
		for (BlockPos pos : CoilHeatScanner.collectPositions(machine.getPos(), machine.getFacing(), machine.getMultiblockId())) { chunks.add(new ChunkPos(pos).toLong()); }
	}
	private static void tickets(ServerWorld world, SpaceTaskIndex.Location location, Set<Long> desired) {
		Set<Long> old = TICKETS.getOrDefault(location, Set.of());
		for (long pos : old) { if (!desired.contains(pos)) { world.getChunkManager().removeTicket(TICKET, new ChunkPos(pos), 2, location.pos()); } }
		for (long pos : desired) { if (!old.contains(pos)) { world.getChunkManager().addTicket(TICKET, new ChunkPos(pos), 2, location.pos()); } }
		TICKETS.put(location, Set.copyOf(desired));
	}
	private static void release(MinecraftServer server, SpaceTaskIndex.Location location) {
		Set<Long> chunks = TICKETS.remove(location);
		ServerWorld world = world(server, location);
		if (world != null && chunks != null) { for (long pos : chunks) { world.getChunkManager().removeTicket(TICKET, new ChunkPos(pos), 2, location.pos()); } }
	}
	private static ServerWorld world(MinecraftServer server, SpaceTaskIndex.Location location) {
		return server.getWorld(RegistryKey.of(RegistryKeys.WORLD, location.dimension()));
	}
}
