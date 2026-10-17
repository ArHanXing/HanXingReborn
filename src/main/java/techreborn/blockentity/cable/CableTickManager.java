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

package techreborn.blockentity.cable;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.server.world.ServerWorld;
import team.reborn.energy.api.EnergyStorage;
import techreborn.config.TechRebornConfig;
import techreborn.init.TRContent;

import java.util.*;

class CableTickManager {
	private static final List<CableBlockEntity> cableList = new ArrayList<>();
	private static final List<OfferedEnergyStorage> targetStorages = new ArrayList<>();
	private static long tickCounter = 0;

	static {
		ServerTickEvents.START_SERVER_TICK.register(server -> tickCounter++);
		// A world change must not leave cables of the previous world registered.
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> CableNetworkManager.clear());
	}

	static void handleCableTick(CableBlockEntity startingCable) {
		if (!(startingCable.getWorld() instanceof ServerWorld)) throw new IllegalStateException();

		// Membership comes from the union-find: it hands back the cached set of
		// cables on this network, rebuilding the component only when the
		// topology changed or a member's chunk unloaded.
		CableNetwork network = CableNetworkManager.resolve(startingCable, tickCounter);
		// Make sure we only gather and tick each network once per tick.
		if (network.size() == 0 || network.lastTick() == tickCounter) return;
		network.setLastTick(tickCounter);

		try {
			cableList.clear();
			cableList.addAll(network.cables());

			// Group all energy into the network.
			long networkCapacity = 0;
			long networkAmount = 0;

			for (CableBlockEntity cable : cableList) {
				networkAmount += cable.energyContainer.amount;
				networkCapacity += cable.energyContainer.getCapacity();

				// Update cable connections.
				cable.appendTargets(targetStorages);
				// Block any cable I/O while we access the network amount directly.
				// Some things might try to access cables, for example a p2p tunnel pointing back at a cable.
				// If the cables and the network go out of sync, we risk duping or voiding energy.
				cable.ioBlocked = true;
			}

			// An idle network whose stored total did not change cannot move any
			// energy, so the two transfer passes are skipped - they are by far the
			// most expensive part, costing one simulated transaction per attached
			// storage per pass. The total is compared rather than trusting the idle
			// flag alone, because something can push energy straight into a cable
			// between ticks and that must still be noticed.
			//
			// A network that moved nothing is still probed periodically: a consumer
			// that was full last tick can become able to accept energy without any
			// block event the cables are able to observe.
			boolean idle = network.isIdle() && networkAmount == network.getBuffer();
			int interval = TechRebornConfig.cableIdleIntervalTicks;
			boolean recheck = interval <= 1 || tickCounter % interval == 0;

			long input = 0;
			long output = 0;
			if (!idle || recheck) {
				// Just in case.
				if (networkAmount > networkCapacity) {
					networkAmount = networkCapacity;
				}

				// Pull energy from storages.
				input = dispatchTransfer(startingCable.getCableType(), EnergyStorage::extract, networkCapacity - networkAmount);
				networkAmount += input;
				// Push energy into storages.
				output = dispatchTransfer(startingCable.getCableType(), EnergyStorage::insert, networkAmount);
				networkAmount -= output;
			}

			// Publish the aggregate before it is split back across the cables, so
			// the Jade tooltip can show what this network actually moved.
			network.setStats(networkAmount, networkCapacity, input, output);

			// Split energy evenly across cables. markDirty() makes the chunk save,
			// so it is only called when the amount really changed; doing it every
			// tick kept every chunk with a cable permanently dirty.
			int cableCount = cableList.size();
			for (CableBlockEntity cable : cableList) {
				long share = networkAmount / cableCount;
				if (cable.energyContainer.amount != share) {
					cable.energyContainer.amount = share;
					cable.markDirty();
				}
				networkAmount -= share;
				cableCount--;
				cable.ioBlocked = false;
			}
		} finally {
			cableList.clear();
			targetStorages.clear();
		}
	}

	/**
	 * Perform a transfer operation across a list of targets.
	 */
	private static long dispatchTransfer(TRContent.Cables cableType, TransferOperation operation, long maxAmount) {
		// Build target list.
		List<SortableStorage> sortedTargets = new ArrayList<>();
		for (var storage : targetStorages) {
			sortedTargets.add(new SortableStorage(operation, storage));
		}
		// Shuffle for better average transfer.
		Collections.shuffle(sortedTargets);
		// Sort by lowest simulation target.
		sortedTargets.sort(Comparator.comparingLong(sortableStorage -> sortableStorage.simulationResult));
		// Actually perform the transfer.
		try (Transaction transaction = Transaction.openOuter()) {
			long transferredAmount = 0;
			for (int i = 0; i < sortedTargets.size(); ++i) {
				SortableStorage target = sortedTargets.get(i);
				int remainingTargets = sortedTargets.size() - i;
				long remainingAmount = maxAmount - transferredAmount;
				// Limit max amount to the cable transfer rate.
				long targetMaxAmount = Math.min(remainingAmount / remainingTargets, cableType.transferRate);

				long localTransferred = operation.transfer(target.storage.storage(), targetMaxAmount, transaction);
				if (localTransferred > 0) {
					transferredAmount += localTransferred;
					// Block duplicate operations.
					target.storage.afterTransfer();
				}
			}
			transaction.commit();
			return transferredAmount;
		}
	}

	private interface TransferOperation {
		long transfer(EnergyStorage storage, long maxAmount, Transaction transaction);
	}

	private static class SortableStorage {
		private final OfferedEnergyStorage storage;
		private final long simulationResult;

		SortableStorage(TransferOperation operation, OfferedEnergyStorage storage) {
			this.storage = storage;
			try (Transaction tx = Transaction.openOuter()) {
				this.simulationResult = operation.transfer(storage.storage(), Long.MAX_VALUE, tx);
			}
		}
	}
}
