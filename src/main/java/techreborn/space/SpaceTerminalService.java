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

package techreborn.space;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import reborncore.common.screen.BuiltScreenHandler;
import techreborn.TechReborn;
import techreborn.blockentity.machine.multiblock.JsonMultiblockMachineBlockEntity;
import techreborn.blockentity.machine.multiblock.SpaceElevatorBlockEntity;
import techreborn.blockentity.machine.multiblock.SpaceElevatorMinerBlockEntity;
import techreborn.space.data.PlanetCatalog;
import techreborn.space.data.PlanetDefinition;
import techreborn.space.task.SpaceTaskEngine;
import techreborn.team.TeamScopes;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Window-scoped, viewer-filtered data and validated controls for the task engine. */
public final class SpaceTerminalService {
	private static final Map<UUID, Session> SESSIONS = new HashMap<>();
	private static final Map<UUID, Long> REQUEST_TICKS = new HashMap<>();
	private static final Map<UUID, Long> CONFIG_ACKS = new HashMap<>();
	private static final Map<UUID, Long> ACTION_ACKS = new HashMap<>();
	private static final Map<UUID, ActionStamp> ACTIONS = new HashMap<>();
	private static long sequence;

	private SpaceTerminalService() {
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> reload(server, false));
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
			if (success) {
				reload(server, true);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SESSIONS.clear();
			REQUEST_TICKS.clear();
			CONFIG_ACKS.clear();
			ACTION_ACKS.clear();
			ACTIONS.clear();
			PlanetCatalog.clear();
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			SESSIONS.remove(handler.player.getUuid());
			REQUEST_TICKS.remove(handler.player.getUuid());
			CONFIG_ACKS.remove(handler.player.getUuid());
			ACTION_ACKS.remove(handler.player.getUuid());
			ACTIONS.remove(handler.player.getUuid());
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTicks() % 10 == 0) {
				for (var player : server.getPlayerManager().getPlayerList()) {
					if (terminal(player) == null) {
						SESSIONS.remove(player.getUuid());
					} else {
						push(player, false, "");
					}
				}
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(SpaceTerminalRequest.ID, (request, context) -> handle(context.player(), request));
		ServerPlayNetworking.registerGlobalReceiver(SpaceTaskAction.ID, (request, context) -> handleAction(context.player(), request));
	}

	private static void reload(MinecraftServer server, boolean cancelTasks) {
		if (PlanetCatalog.reload(server.getResourceManager(), server.getRegistryManager(), () -> {
			if (cancelTasks) {
				SpaceTaskEngine.beforeReload(server);
			}
			SESSIONS.clear();
		})) {
			for (var player : server.getPlayerManager().getPlayerList()) {
				push(player, true, "");
			}
		}
	}

	public static SpaceMachineState state(BlockEntity machine) {
		if (machine instanceof SpaceElevatorBlockEntity host) {
			return host.getSpaceState();
		}
		if (machine instanceof SpaceElevatorMinerBlockEntity miner) {
			return miner.getSpaceState();
		}
		return null;
	}

	private static JsonMultiblockMachineBlockEntity terminal(ServerPlayerEntity player) {
		if (!(player.currentScreenHandler instanceof BuiltScreenHandler screen)
				|| !(screen.getBlockEntity() instanceof JsonMultiblockMachineBlockEntity machine)
				|| state(machine) == null || machine.getWorld() != player.getServerWorld()
				|| !player.getServerWorld().isChunkLoaded(machine.getPos())
				|| player.getServerWorld().getBlockEntity(machine.getPos()) != machine
				|| player.squaredDistanceTo(machine.getPos().toCenterPos()) > 16 * 16 || !screen.canUse(player)) {
			return null;
		}
		return machine;
	}

	private static void handle(ServerPlayerEntity player, SpaceTerminalRequest request) {
		var machine = terminal(player);
		if (machine == null || player.currentScreenHandler.syncId != request.syncId() || !machine.getPos().equals(request.pos())) {
			return;
		}
		long tick = player.getServerWorld().getTime();
		Long previous = REQUEST_TICKS.get(player.getUuid());
		if (!request.configure() && previous != null && tick - previous < 2 && tick >= previous) {
			return;
		}
		REQUEST_TICKS.put(player.getUuid(), tick);
		if (!request.configure()) {
			push(player, false, "");
			return;
		}
		CONFIG_ACKS.merge(player.getUuid(), 1L, Long::sum);
		String reason = validate(player, machine, request);
		if (reason.isEmpty()) {
			Identifier body = Identifier.tryParse(request.body());
			if (!state(machine).applySelection(player, body, request.tier(), request.resources())) {
				reason = "no_permission";
			}
		}
		push(player, true, reason);
	}

	private static void handleAction(ServerPlayerEntity player, SpaceTaskAction action) {
		var machine = terminal(player);
		if (machine == null || player.currentScreenHandler.syncId != action.syncId() || !machine.getPos().equals(action.pos())) {
			return;
		}
		long tick = player.getServerWorld().getTime();
		ActionStamp previous = ACTIONS.get(player.getUuid());
		if (previous != null && previous.tick() == tick && previous.action().equals(action)) {
			return; // Do not double-toggle from duplicate transport input.
		}
		ACTIONS.put(player.getUuid(), new ActionStamp(tick, action));
		ACTION_ACKS.merge(player.getUuid(), 1L, Long::sum);
		String reason;
		if (!state(machine).canConfigure(player)) {
			reason = "no_permission";
		} else if (action.catalogRevision() != PlanetCatalog.revision()) {
			reason = "stale_catalog";
		} else {
			boolean enabling = action.action() == SpaceTaskAction.Action.TOGGLE_AUTO
					&& !SpaceTaskEngine.view(machine, player).getBoolean("autoEnabled");
			boolean starting = action.action() == SpaceTaskAction.Action.START || enabling;
			if (starting && !machine.isMultiblockValid()) {
				reason = "structure_missing";
			} else if (starting && (state(machine).selectedBody() == null
					|| !state(machine).selectedBody().toString().equals(action.expectedBody())
					|| state(machine).selectedVariantTier() != action.expectedTier())) {
				reason = "selection_pending";
			} else {
				reason = switch (action.action()) {
					case START -> machine instanceof SpaceElevatorBlockEntity ? SpaceTaskEngine.start(player, machine) : "invalid_selection";
					case TOGGLE_AUTO -> machine instanceof SpaceElevatorMinerBlockEntity ? SpaceTaskEngine.toggleAuto(player, machine) : "invalid_selection";
					case TOGGLE_PAUSE -> SpaceTaskEngine.togglePause(player, machine);
				};
			}
		}
		push(player, true, reason);
	}

	private record ActionStamp(long tick, SpaceTaskAction action) {
	}

	private static String validate(ServerPlayerEntity player, JsonMultiblockMachineBlockEntity machine, SpaceTerminalRequest request) {
		if (!state(machine).canConfigure(player)) {
			return "no_permission";
		}
		if (request.catalogRevision() != PlanetCatalog.revision()) {
			return "stale_catalog";
		}
		if (!machine.isMultiblockValid()) {
			return "structure_missing";
		}
		Identifier id = request.body().isBlank() ? null : Identifier.tryParse(request.body());
		PlanetDefinition body = id == null ? null : PlanetCatalog.get(id);
		if (body == null) {
			return "invalid_selection";
		}
		int stage = SpaceProgressState.get(player.getServer()).stage(TeamScopes.scopeOf(player), id);
		List<PlanetDefinition.Variant> variants = variants(body, stage, machine instanceof SpaceElevatorMinerBlockEntity);
		if (variants.stream().noneMatch(v -> v.tier() == request.tier())) {
			return "scheme_unavailable";
		}
		if (new HashSet<>(request.resources()).size() != request.resources().size()) {
			return "invalid_selection";
		}
		if (machine instanceof SpaceElevatorBlockEntity && !request.resources().isEmpty()) {
			return "invalid_selection";
		}
		if (!request.resources().isEmpty() && (!visible(body.visibility().resources(), stage)
				|| request.resources().stream().anyMatch(resource -> body.resources().stream().noneMatch(r -> r.id().equals(resource))))) {
			return "invalid_selection";
		}
		return "";
	}

	public static boolean visible(String visibility, int stage) {
		return switch (visibility) {
			case "always" -> true;
			case "after_survey" -> stage >= SpaceProgressState.SURVEYED;
			case "after_land" -> stage >= SpaceProgressState.LANDED;
			default -> false;
		};
	}

	public static List<PlanetDefinition.Variant> variants(PlanetDefinition body, int stage, boolean miner) {
		if (miner) {
			return body.missions().mining().enabled() ? body.missions().mining().variants() : List.of();
		}
		PlanetDefinition.Stage mission = switch (stage) {
			case 0 -> body.missions().survey();
			case 1 -> body.missions().land();
			case 2 -> body.missions().outpost();
			default -> null;
		};
		return mission != null && mission.enabled() ? mission.variants() : List.of();
	}

	private static void push(ServerPlayerEntity player, boolean force, String reason) {
		var machine = terminal(player);
		if (machine == null || !ServerPlayNetworking.canSend(player, SpaceTerminalSnapshot.ID)) {
			return;
		}
		var state = state(machine);
		UUID scope = TeamScopes.scopeOf(player);
		var progress = SpaceProgressState.get(player.getServer());
		boolean structure = machine.isMultiblockValid();
		boolean editable = state.canConfigure(player);
		Session session = new Session(player.currentScreenHandler.syncId, machine.getPos().asLong(), scope,
				PlanetCatalog.revision(), progress.revision(scope), state.revision(), SpaceTaskEngine.revision(machine), structure, editable);
		if (!force && session.equals(SESSIONS.get(player.getUuid()))) {
			return;
		}
		NbtCompound snapshot = snapshot(machine, state, progress, scope, structure, editable, reason);
		snapshot.put("task", SpaceTaskEngine.view(machine, player));
		snapshot.putLong("configurationAck", CONFIG_ACKS.getOrDefault(player.getUuid(), 0L));
		snapshot.putLong("actionAck", ACTION_ACKS.getOrDefault(player.getUuid(), 0L));
		RegistryByteBuf buffer = new RegistryByteBuf(Unpooled.buffer(), player.getServerWorld().getRegistryManager());
		try {
			buffer.writeNbt(snapshot);
			int length = buffer.readableBytes();
			int parts = Math.max(1, (length + SpaceTerminalSnapshot.PART_SIZE - 1) / SpaceTerminalSnapshot.PART_SIZE);
			if (parts > SpaceTerminalSnapshot.MAX_PARTS) {
				TechReborn.LOGGER.error("Space terminal snapshot exceeds 1 MiB; reduce component payloads or resources");
				return;
			}
			byte[] bytes = new byte[length];
			buffer.readBytes(bytes);
			long serial = ++sequence;
			for (int part = 0; part < parts; part++) {
				int start = part * SpaceTerminalSnapshot.PART_SIZE;
				ServerPlayNetworking.send(player, new SpaceTerminalSnapshot(session.syncId(), machine.getPos(), serial,
						part, parts, Arrays.copyOfRange(bytes, start, Math.min(length, start + SpaceTerminalSnapshot.PART_SIZE))));
			}
			SESSIONS.put(player.getUuid(), session);
		} finally {
			buffer.release();
		}
	}

	/** Constructs the only client-visible copy; hidden fields are never serialized. */
	static NbtCompound snapshot(JsonMultiblockMachineBlockEntity machine, SpaceMachineState state, SpaceProgressState progress,
			UUID scope, boolean structure, boolean editable, String reason) {
		NbtCompound root = new NbtCompound();
		root.putLong("catalogRevision", PlanetCatalog.revision());
		root.putLong("progressRevision", progress.revision(scope));
		root.putBoolean("editable", editable);
		root.putBoolean("structure", structure);
		root.putString("reason", reason);
		// Other teams can inspect the public map, not this owner's private target or selection.
		if (editable) {
			root.putString("selectedBody", state.selectedBody() == null ? "" : state.selectedBody().toString());
			root.putInt("selectedTier", state.selectedVariantTier());
			NbtList selected = new NbtList();
			state.selectedResources().forEach(id -> selected.add(NbtString.of(id)));
			root.put("selectedResources", selected);
		}
		NbtList bodies = new NbtList();
		PlanetCatalog.definitions().values().stream().sorted(Comparator.comparingInt(PlanetDefinition::sortOrder)
				.thenComparing(body -> body.id().toString())).forEach(body -> {
			int stage = progress.stage(scope, body.id());
			bodies.add(bodyView(body, stage, machine instanceof SpaceElevatorMinerBlockEntity, machine.getWorld().getRegistryManager()));
		});
		root.put("bodies", bodies);
		return root;
	}

	public static NbtCompound bodyView(PlanetDefinition body, int stage, boolean miner,
			net.minecraft.registry.RegistryWrapper.WrapperLookup lookup) {
		NbtCompound view = new NbtCompound();
		view.putString("id", body.id().toString());
		view.putString("system", body.system());
		view.putString("parent", body.parent() == null ? "" : body.parent().toString());
		view.putString("type", body.type());
		view.putInt("tier", body.tier());
		view.putInt("stage", stage);
		view.putString("marker", Registries.BLOCK.getId(body.markerBlock()).toString());
		view.putDouble("markerScale", body.markerScale());
		view.putDouble("orbitRadius", body.physical().orbitRadiusAu());
		view.putDouble("phase", body.physical().orbitPhaseDeg());
		boolean name = visible(body.visibility().name(), stage);
		view.putString("nameKey", name ? body.display().nameKey() : "gui.techreborn.space.unknown_body");
		if (name) {
			view.putString("descriptionKey", body.display().descriptionKey());
		}
		boolean physical = visible(body.visibility().physical(), stage);
		view.putBoolean("physicalVisible", physical);
		if (physical) {
			view.putDouble("radius", body.physical().radiusKm());
			view.putDouble("temperature", body.physical().temperatureK());
			view.putDouble("rotation", body.physical().rotationPeriodHours());
			view.putDouble("period", body.physical().orbitalPeriodDays());
			NbtList features = new NbtList();
			body.physical().orbitFeatures().forEach(feature -> features.add(NbtString.of(feature)));
			view.put("features", features);
		}
		boolean resourcesVisible = visible(body.visibility().resources(), stage);
		view.putBoolean("resourcesVisible", resourcesVisible);
		if (resourcesVisible) {
			NbtList resources = new NbtList();
			body.resources().forEach(resource -> {
				NbtCompound entry = new NbtCompound();
				entry.putString("id", resource.id());
				entry.putInt("count", resource.stack().getCount());
				entry.put("stack", resource.stack().copyWithCount(1).encode(lookup));
				resources.add(entry);
			});
			view.put("resources", resources);
		}
		view.putBoolean("miningEnabled", body.missions().mining().enabled());
		view.putBoolean("requiresLanding", body.missions().mining().requiresLanding());
		view.putInt("time", body.time());
		NbtList schemes = new NbtList();
		// Before survey only the survey supplies are public. Mining supplies wait
		// for both survey and the resource visibility gate.
		if (!miner || stage >= 1 && resourcesVisible) {
			for (var variant : variants(body, stage, miner)) {
				NbtCompound entry = new NbtCompound();
				entry.putInt("tier", variant.tier());
				entry.putLong("time", missionTime(body, stage, miner, variant.tier()));
				entry.putLong("power", power(variant.tier()));
				entry.put("inputs", stacks(variant.inputs(), lookup));
				entry.put("outputs", stacks(variant.outputs(), lookup));
				schemes.add(entry);
			}
		}
		view.put("variants", schemes);
		return view;
	}

	private static NbtList stacks(List<ItemStack> stacks, net.minecraft.registry.RegistryWrapper.WrapperLookup lookup) {
		NbtList result = new NbtList();
		stacks.forEach(stack -> {
			NbtCompound entry = new NbtCompound();
			entry.putInt("count", stack.getCount());
			entry.put("stack", stack.copyWithCount(1).encode(lookup));
			result.add(entry);
		});
		return result;
	}

	public static long power(int tier) {
		return switch (tier) {
			case 1 -> 512;
			case 2 -> 2048;
			case 3 -> 8192;
			default -> throw new IllegalArgumentException("Space tier must be 1..3");
		};
	}

	public static long missionTime(PlanetDefinition body, int stage, boolean miner, int tier) {
		int base = miner ? body.time() : switch (stage) {
			case 0 -> body.missions().survey().time();
			case 1 -> body.missions().land().time();
			case 2 -> body.missions().outpost().time();
			default -> 1;
		};
		// Use checked long arithmetic: a valid base int can exceed int range at x5.
		long ticks = (long) base * (miner && (stage < 3 || body.type().equals("black_hole")) ? 5 : 1);
		ticks /= 1L << (2 * Math.max(0, tier - body.tier()));
		return Math.max(1, ticks);
	}

	private record Session(int syncId, long pos, UUID scope, long catalog, long progress, long machine, long task,
			boolean structure, boolean editable) {
	}
}
