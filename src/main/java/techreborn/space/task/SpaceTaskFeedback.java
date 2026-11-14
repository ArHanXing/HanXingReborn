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

import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import techreborn.TechReborn;
import techreborn.blockentity.machine.multiblock.JsonMultiblockMachineBlockEntity;
import techreborn.space.SpaceTerminalService;

/** Cosmetic only: real server transitions drive bounded, owner-filtered effects. */
public final class SpaceTaskFeedback {
	private SpaceTaskFeedback() { }

	public static void transition(JsonMultiblockMachineBlockEntity machine, SpaceTask task, String event) {
		if (!(machine.getWorld() instanceof ServerWorld world) || task == null) { return; }
		try {
			int cooldown = task.kind() == SpaceTask.Kind.MINING ? 40 : 0;
			if (!SpaceTaskEngine.container(machine).visualEvent(event, task.body().toString(), world.getTime(), cooldown)) { return; }
			for (ServerPlayerEntity player : world.getPlayers()) {
				if (!canSee(machine, player)) { continue; }
				double x = machine.getPos().getX() + .5, y = machine.getPos().getY() + 1.2, z = machine.getPos().getZ() + .5;
				if (event.equals("launch")) {
					for (int i = 0; i < 5; i++) { world.spawnParticles(player, ParticleTypes.END_ROD, false, x, y + i * .65, z, 2, .12, .1, .12, .025); }
				} else if (event.equals("complete")) {
					world.spawnParticles(player, ParticleTypes.WAX_ON, false, x, y, z, 18, .6, .4, .6, .08);
				} else {
					world.spawnParticles(player, ParticleTypes.ELECTRIC_SPARK, false, x, y, z, 6, .35, .2, .35, .025);
				}
				if (task.kind() != SpaceTask.Kind.MINING || event.equals("complete")) {
					player.playSoundToPlayer(event.equals("complete") ? SoundEvents.BLOCK_BEACON_ACTIVATE
							: event.equals("launch") ? SoundEvents.BLOCK_BEACON_POWER_SELECT : SoundEvents.BLOCK_BEACON_DEACTIVATE,
							SoundCategory.BLOCKS, .25F, event.equals("complete") ? 1.5F : 1F);
				}
			}
		} catch (RuntimeException exception) {
			TechReborn.LOGGER.debug("Space task cosmetic feedback skipped: {}", exception.toString());
		}
	}

	public static void running(JsonMultiblockMachineBlockEntity machine, SpaceTask task) {
		if (!(machine.getWorld() instanceof ServerWorld world) || (world.getTime() + machine.getPos().asLong()) % 40 != 0) { return; }
		try {
			for (ServerPlayerEntity player : world.getPlayers()) {
				if (canSee(machine, player)) {
					world.spawnParticles(player, task.kind() == SpaceTask.Kind.SURVEY ? ParticleTypes.ENCHANT : ParticleTypes.END_ROD,
							false, machine.getPos().getX() + .5, machine.getPos().getY() + 1.6, machine.getPos().getZ() + .5,
							3, .25, .35, .25, .025);
				}
			}
		} catch (RuntimeException exception) {
			TechReborn.LOGGER.debug("Space task running effect skipped: {}", exception.toString());
		}
	}

	private static boolean canSee(JsonMultiblockMachineBlockEntity machine, ServerPlayerEntity player) {
		return player.squaredDistanceTo(machine.getPos().toCenterPos()) <= 32 * 32
				&& SpaceTerminalService.state(machine).canConfigure(player);
	}
}
