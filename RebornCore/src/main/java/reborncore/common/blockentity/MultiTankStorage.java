/*
 * This file is part of RebornCore, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021 TeamReborn
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

package reborncore.common.blockentity;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import reborncore.common.util.Tank;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A {@link Storage} view over every tank of a machine with more than one.
 * <p>
 * The global fluid lookup used to hand out {@code getTank()} only, which meant
 * a multi-tank machine was reachable through its first tank alone and the other
 * tanks could never be filled. This adapter exposes them all: iteration yields
 * one view per tank so pipes and item-based fillers can see them, and insertion
 * fans out over the tanks, which is enough for the "just fill this machine"
 * case.
 * <p>
 * Selection of which tank an external machine may touch is the job of the
 * per-tank fluid IO config, not of this view; the config still drives the
 * face-by-face transfers.
 */
public class MultiTankStorage implements Storage<FluidVariant> {

	private final MachineBaseBlockEntity machine;

	public MultiTankStorage(MachineBaseBlockEntity machine) {
		this.machine = machine;
	}

	private List<Tank> tanks() {
		List<Tank> tanks = new ArrayList<>();
		for (int i = 0; i < machine.getTankCount(); i++) {
			Tank tank = machine.getTank(i);
			if (tank != null) {
				tanks.add(tank);
			}
		}
		return tanks;
	}

	@Override
	public boolean supportsInsertion() {
		return true;
	}

	@Override
	public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
		long inserted = 0;
		for (Tank tank : tanks()) {
			long remaining = maxAmount - inserted;
			if (remaining <= 0) {
				break;
			}
			// A tank that already holds something else rejects the variant itself,
			// so there is no need to pre-filter here.
			inserted += tank.insert(resource, remaining, transaction);
		}
		return inserted;
	}

	@Override
	public boolean supportsExtraction() {
		return true;
	}

	@Override
	public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
		long extracted = 0;
		for (Tank tank : tanks()) {
			long remaining = maxAmount - extracted;
			if (remaining <= 0) {
				break;
			}
			extracted += tank.extract(resource, remaining, transaction);
		}
		return extracted;
	}

	@Override
	public Iterator<StorageView<FluidVariant>> iterator() {
		List<StorageView<FluidVariant>> views = new ArrayList<>();
		for (Tank tank : tanks()) {
			views.add(tank);
		}
		return views.iterator();
	}
}
