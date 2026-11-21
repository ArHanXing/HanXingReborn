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

package reborncore.common.util;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import reborncore.common.fluid.FluidValue;
import reborncore.common.fluid.container.FluidInstance;
import reborncore.common.screen.Syncable;
import reborncore.common.util.serialization.SerializationUtil;

import java.util.function.UnaryOperator;

public class Tank extends SnapshotParticipant<FluidInstance> implements Syncable, SingleSlotStorage<FluidVariant> {
	private final String name;
	private FluidInstance fluidInstance = new FluidInstance();
	private final FluidValue capacity;

	/**
	 * Notified after a transfer transaction actually commits, so the owning block
	 * entity can refresh its comparator output and push the new contents to open
	 * GUIs.
	 * <p>
	 * This is what makes an externally driven change (a pipe, another machine, a
	 * hopper) visible at all: before, only the machine's own code paths touched the
	 * tank, so nothing told the world the contents had moved.
	 */
	@Nullable
	private Runnable changeListener;

	public Tank(String name, FluidValue capacity) {
		super();
		this.name = name;
		this.capacity = capacity;
	}

	@NotNull
	public FluidInstance getFluidInstance() {
		return fluidInstance;
	}

	@NotNull
	public Fluid getFluid() {
		return getFluidInstance().fluid();
	}

	public FluidValue getFluidValueCapacity() {
		return capacity;
	}

	public FluidValue getFreeSpace() {
		return getFluidValueCapacity().subtract(getFluidAmount());
	}

	public boolean canFit(Fluid fluid, FluidValue amount) {
		return (isEmpty() || getFluid() == fluid) && getFreeSpace().equalOrMoreThan(amount);
	}

	public boolean isEmpty() {
		return getFluidInstance().isEmpty();
	}

	public boolean isFull() {
		return !getFluidInstance().isEmpty() && getFluidInstance().getAmount().equalOrMoreThan(getFluidValueCapacity());
	}

	public final NbtCompound write(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapperLookup) {
		NbtCompound tankData = SerializationUtil.writeNbt(FluidInstance.CODEC, fluidInstance, wrapperLookup);
		nbt.put(name, tankData);
		return nbt;
	}

	public void setFluidAmount(FluidValue amount) {
		if (!fluidInstance.isEmptyFluid()) {
			modifyFluid(fluidInstance -> fluidInstance.withAmount(amount));
		}
	}

	public final Tank read(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapperLookup) {
		if (nbt.contains(name)) {
			// allow reading empty tanks
			setFluid(Fluids.EMPTY);

			NbtCompound tankData = nbt.getCompound(name);
			fluidInstance = SerializationUtil.parseNbt(FluidInstance.CODEC, tankData, wrapperLookup, () -> FluidInstance.EMPTY, "tank data");
		}
		return this;
	}

	public void setFluid(@NotNull Fluid f) {
		modifyFluid(fluidInstance -> fluidInstance.withFluid(f));
	}

	@Override
	public void configureSync(Context context) {
		context.sync(FluidInstance.PACKET_CODEC, this::getFluidInstance, this::setFluidInstance);
	}

	public FluidValue getFluidAmount() {
		return getFluidInstance().getAmount();
	}

	public void modifyFluid(UnaryOperator<FluidInstance> operator) {
		setFluidInstance(operator.apply(fluidInstance));
	}

	/**
	 * Sets the contents directly, for code that does not run inside a transfer
	 * transaction (recipe output, cell handling, admin commands).
	 * <p>
	 * Inside a transaction the change is not announced here: {@link #onFinalCommit}
	 * does it once the outer transaction actually commits, so an aborted transfer
	 * never reports contents that were rolled back.
	 *
	 * @param fluidInstance {@link FluidInstance} the new contents
	 */
	public void setFluidInstance(@NotNull FluidInstance fluidInstance) {
		boolean changed = !this.fluidInstance.equals(fluidInstance);
		this.fluidInstance = fluidInstance;
		// Only notify when no transaction is open: inside one, onFinalCommit does it
		// once the transfer actually commits, so an aborted transfer never reports
		// contents that were rolled back.
		if (changed && changeListener != null && !isInTransaction()) {
			changeListener.run();
		}
	}

	/**
	 * @return {@code boolean} whether a transfer transaction is currently open on
	 *         this thread
	 */
	private static boolean isInTransaction() {
		try {
			return Transaction.getCurrentUnsafe() != null;
		} catch (IllegalStateException e) {
			// Thrown from a transaction close callback ("May not call getCurrentUnsafe()
			// from a close callback"). A change made there is either a rollback or part
			// of a commit that onFinalCommit already covers, so treat it as "inside".
			return true;
		}
	}

	/**
	 * Registers the owner's refresh hook. Called with no arguments, on the server
	 * thread, after every committed transfer.
	 *
	 * @param changeListener {@link Runnable} the hook, or {@code null} to clear it
	 */
	public void setChangeListener(@Nullable Runnable changeListener) {
		this.changeListener = changeListener;
	}

	@Override
	protected void onFinalCommit() {
		if (changeListener != null) {
			changeListener.run();
		}
	}

	@Override
	public long insert(FluidVariant insertedVariant, long maxAmount, TransactionContext transaction) {
		StoragePreconditions.notBlankNotNegative(insertedVariant, maxAmount);
		FluidVariant currentVariant = getResource();

		if (currentVariant.equals(insertedVariant) || currentVariant.isBlank()) {
			long insertedAmount = Math.min(maxAmount, getCapacity() - getAmount());

			if (insertedAmount > 0) {
				updateSnapshots(transaction);

				// Just in case.
				if (currentVariant.isBlank()) {
					modifyFluid(fluidInstance -> fluidInstance.withAmount(FluidValue.EMPTY));
				}

				modifyFluid(fluidInstance -> fluidInstance.withFluid(insertedVariant.getFluid()).addAmount(FluidValue.fromRaw(insertedAmount)));
			}

			return insertedAmount;
		}

		return 0;
	}

	@Override
	public long extract(FluidVariant extractedVariant, long maxAmount, TransactionContext transaction) {
		StoragePreconditions.notBlankNotNegative(extractedVariant, maxAmount);
		FluidVariant currentVariant = getResource();

		if (extractedVariant.equals(currentVariant)) {
			long extractedAmount = Math.min(maxAmount, getAmount());

			if (extractedAmount > 0) {
				updateSnapshots(transaction);

				modifyFluid(fluidInstance -> fluidInstance.subtractAmount(FluidValue.fromRaw(extractedAmount)));
			}

			return extractedAmount;
		}

		return 0;
	}

	@Override
	public boolean isResourceBlank() {
		return getResource().isBlank();
	}

	@Override
	public FluidVariant getResource() {
		return fluidInstance.fluidVariant();
	}

	@Override
	@SuppressWarnings({"deprecation"})
	public long getAmount() {
		return fluidInstance.getAmount().getRawValue();
	}

	@Override
	@SuppressWarnings({"deprecation"})
	public long getCapacity() {
		return getFluidValueCapacity().getRawValue();
	}

	@Override
	protected FluidInstance createSnapshot() {
		return fluidInstance;
	}

	@Override
	protected void readSnapshot(FluidInstance snapshot) {
		// Plain field write on purpose: this runs from a transaction close callback
		// during a rollback, where both Transaction.getCurrentUnsafe() and any
		// listener notification are off limits - the transaction never happened, so
		// there is nothing to announce.
		this.fluidInstance = snapshot;
	}
}
