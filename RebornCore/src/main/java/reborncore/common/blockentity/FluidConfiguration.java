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

import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import reborncore.common.util.NBTSerializable;
import reborncore.common.util.Tank;

import java.util.*;

/**
 * Per-tank fluid IO configuration.
 * <p>
 * The tanks are picked first and configured second, mirroring the item side:
 * every tank of a machine gets its own {@link TankConfigHolder} carrying a face
 * map plus the pull-in / pump-out switches. A machine with a single tank simply
 * has one holder, so the UI degenerates to the old behaviour without the code
 * having to special-case it.
 * <p>
 * The number of tanks comes from {@link MachineBaseBlockEntity#getTankCount()}.
 */
public class FluidConfiguration implements NBTSerializable {
	private static final PacketCodec<ByteBuf, Map<Direction, FluidConfig>> SIDE_MAP_PACKET_CODEC = PacketCodecs.map(
		HashMap::new,
		Direction.PACKET_CODEC,
		FluidConfig.PACKET_CODEC
	);
	public static final PacketCodec<ByteBuf, FluidConfiguration> PACKET_CODEC = TankConfigHolder.PACKET_CODEC
		.collect(PacketCodecs.toList())
		.xmap(FluidConfiguration::new, FluidConfiguration::getTankDetails);

	List<TankConfigHolder> tankDetails = new ArrayList<>();

	public FluidConfiguration() {
		updateTankDetails(new TankConfigHolder(0));
	}

	public FluidConfiguration(NbtCompound tagCompound) {
		read(tagCompound);
	}

	private FluidConfiguration(List<TankConfigHolder> tankDetails) {
		this.tankDetails = tankDetails;
	}

	/**
	 * @return {@link List} the holder of every configurable tank
	 */
	public List<TankConfigHolder> getTankDetails() {
		return tankDetails;
	}

	/**
	 * @param tankID {@code int} the tank index
	 * @return {@link TankConfigHolder} the holder, or {@code null} if absent
	 */
	@Nullable
	public TankConfigHolder getTankDetails(int tankID) {
		for (TankConfigHolder holder : tankDetails) {
			if (holder.getTankID() == tankID) {
				return holder;
			}
		}
		return null;
	}

	/**
	 * Adds the holder or replaces the existing one with the same tank index.
	 *
	 * @param holder {@link TankConfigHolder} the holder to store
	 * @return {@link TankConfigHolder} the stored holder
	 */
	public TankConfigHolder updateTankDetails(TankConfigHolder holder) {
		for (int i = 0; i < tankDetails.size(); i++) {
			if (tankDetails.get(i).getTankID() == holder.getTankID()) {
				tankDetails.set(i, holder);
				return holder;
			}
		}
		tankDetails.add(holder);
		return holder;
	}

	/**
	 * Makes sure a holder exists for every tank the machine exposes, so a machine
	 * that gained a tank, or a save that predates per-tank config, still has
	 * something to edit.
	 *
	 * @param machineBase {@link MachineBaseBlockEntity} the machine
	 */
	public void ensureTanks(MachineBaseBlockEntity machineBase) {
		for (int i = 0; i < machineBase.getTankCount(); i++) {
			if (getTankDetails(i) == null) {
				updateTankDetails(new TankConfigHolder(i));
			}
		}
	}

	public void update(MachineBaseBlockEntity machineBase) {
		ensureTanks(machineBase);
		if (machineBase.getWorld() == null || machineBase.getWorld().getTime() % machineBase.slotTransferSpeed() != 0) {
			return;
		}

		for (TankConfigHolder holder : tankDetails) {
			if (!holder.autoInput() && !holder.autoOutput()) {
				continue;
			}
			@Nullable Tank tank = machineBase.getTank(holder.getTankID());
			if (tank == null) {
				continue;
			}
			for (Direction facing : Direction.values()) {
				FluidConfig fluidConfig = holder.getSideDetail(facing);
				if (fluidConfig == null || !fluidConfig.getIoConfig().isEnabled()) {
					continue;
				}

				@Nullable Storage<FluidVariant> neighbour = getTank(machineBase, facing);
				if (holder.autoInput() && fluidConfig.getIoConfig().isInsert()) {
					StorageUtil.move(neighbour, tank, FluidConfiguration::isTransferable, machineBase.fluidTransferAmount().getRawValue(), null);
				}
				if (holder.autoOutput() && fluidConfig.getIoConfig().isExtract()) {
					StorageUtil.move(tank, neighbour, FluidConfiguration::isTransferable, machineBase.fluidTransferAmount().getRawValue(), null);
				}
			}
		}
	}

	/**
	 * Filter for {@link StorageUtil#move}, rejecting blank variants.
	 * <p>
	 * {@code move} walks the source's non-empty views and hands the view's
	 * resource straight to the target's {@code insert}, which enforces
	 * {@code StoragePreconditions.notBlank}. Fabric documents that a filter is
	 * never called with a blank resource, but that guarantee only holds while
	 * the source honours its own contract ({@code isResourceBlank() == false}
	 * implies {@code getResource()} is not blank).
	 * <p>
	 * Refined Storage 2's fluid storage does not, which turned an ordinary
	 * machine tick into {@code IllegalArgumentException: Transfer variant may
	 * not be blank}. Returning {@code false} here drops that view before the
	 * insert, so a misbehaving neighbour can no longer crash the machine.
	 *
	 * @param variant {@link FluidVariant} the resource a view offers
	 * @return {@code boolean} whether it is safe to move
	 */
	private static boolean isTransferable(FluidVariant variant) {
		return !variant.isBlank();
	}

	@Nullable
	private Storage<FluidVariant> getTank(MachineBaseBlockEntity machine, Direction facing) {
		BlockPos pos = machine.getPos().offset(facing);
		return FluidStorage.SIDED.find(machine.getWorld(), pos, facing.getOpposite());
	}

	@NotNull
	@Override
	public NbtCompound write() {
		NbtCompound compound = new NbtCompound();
		NbtCompound tanks = new NbtCompound();
		for (TankConfigHolder holder : tankDetails) {
			tanks.put(String.valueOf(holder.getTankID()), holder.write());
		}
		compound.put("tanks", tanks);
		return compound;
	}

	@Override
	public void read(@NotNull NbtCompound nbt) {
		tankDetails.clear();
		if (nbt.contains("tanks")) {
			NbtCompound tanks = nbt.getCompound("tanks");
			for (String key : tanks.getKeys()) {
				try {
					tankDetails.add(new TankConfigHolder(Integer.parseInt(key), tanks.getCompound(key)));
				} catch (NumberFormatException ignored) {
					// Not a tank entry, nothing sensible to do with it.
				}
			}
		}
		if (tankDetails.isEmpty()) {
			// Legacy layout: one flat side map plus input/output switches. Migrate
			// it onto tank 0 so existing worlds keep their plumbing.
			tankDetails.add(new TankConfigHolder(0, nbt));
		}
	}

	/**
	 * One tank's configuration: a face map plus the pull-in / pump-out switches.
	 */
	public static class TankConfigHolder implements NBTSerializable {
		public static final PacketCodec<ByteBuf, TankConfigHolder> PACKET_CODEC = PacketCodec.tuple(
			PacketCodecs.INTEGER, TankConfigHolder::getTankID,
			SIDE_MAP_PACKET_CODEC, TankConfigHolder::getSideMap,
			PacketCodecs.BOOL, TankConfigHolder::autoInput,
			PacketCodecs.BOOL, TankConfigHolder::autoOutput,
			TankConfigHolder::new
		);

		private final int tankID;
		Map<Direction, FluidConfig> sideMap = new HashMap<>();
		boolean input, output;

		public TankConfigHolder(int tankID) {
			this.tankID = tankID;
			Arrays.stream(Direction.values()).forEach(facing -> sideMap.put(facing, new FluidConfig(facing)));
		}

		public TankConfigHolder(int tankID, Map<Direction, FluidConfig> sideMap, boolean input, boolean output) {
			this.tankID = tankID;
			this.sideMap = sideMap;
			this.input = input;
			this.output = output;
		}

		public TankConfigHolder(int tankID, NbtCompound tagCompound) {
			this.tankID = tankID;
			read(tagCompound);
		}

		public int getTankID() {
			return tankID;
		}

		public Map<Direction, FluidConfig> getSideMap() {
			return sideMap;
		}

		public FluidConfig getSideDetail(Direction side) {
			if (side == null) {
				return sideMap.get(Direction.NORTH);
			}
			return sideMap.get(side);
		}

		public void updateFluidConfig(FluidConfig config) {
			FluidConfig toEdit = sideMap.get(config.side);
			if (toEdit != null) {
				toEdit.ioConfig = config.ioConfig;
			}
		}

		public boolean autoInput() {
			return input;
		}

		public boolean autoOutput() {
			return output;
		}

		public void setInput(boolean input) {
			this.input = input;
		}

		public void setOutput(boolean output) {
			this.output = output;
		}

		@NotNull
		@Override
		public NbtCompound write() {
			NbtCompound compound = new NbtCompound();
			Arrays.stream(Direction.values()).forEach(facing ->
				compound.put("side_" + facing.ordinal(), sideMap.get(facing).write()));
			compound.putBoolean("input", input);
			compound.putBoolean("output", output);
			return compound;
		}

		@Override
		public void read(@NotNull NbtCompound nbt) {
			sideMap.clear();
			Arrays.stream(Direction.values()).forEach(facing -> {
				NbtCompound compound = nbt.getCompound("side_" + facing.ordinal());
				FluidConfig config = new FluidConfig(compound);
				sideMap.put(facing, config);
			});
			input = nbt.getBoolean("input");
			output = nbt.getBoolean("output");
		}
	}

	public static class FluidConfig implements NBTSerializable {
		public static final PacketCodec<ByteBuf, FluidConfig> PACKET_CODEC = PacketCodec.tuple(
			Direction.PACKET_CODEC, FluidConfig::getSide,
			ExtractConfig.PACKET_CODEC, FluidConfig::getIoConfig,
			FluidConfig::new
		);

		Direction side;
		FluidConfiguration.ExtractConfig ioConfig;

		public FluidConfig(Direction side) {
			this.side = side;
			this.ioConfig = ExtractConfig.ALL;
		}

		public FluidConfig(Direction side, FluidConfiguration.ExtractConfig ioConfig) {
			this.side = side;
			this.ioConfig = ioConfig;
		}

		public FluidConfig(NbtCompound tagCompound) {
			read(tagCompound);
		}

		public Direction getSide() {
			return side;
		}

		public ExtractConfig getIoConfig() {
			return ioConfig;
		}

		@NotNull
		@Override
		public NbtCompound write() {
			NbtCompound tagCompound = new NbtCompound();
			tagCompound.putInt("side", side.ordinal());
			tagCompound.putInt("config", ioConfig.ordinal());
			return tagCompound;
		}

		@Override
		public void read(@NotNull NbtCompound nbt) {
			side = Direction.values()[nbt.getInt("side")];
			ioConfig = FluidConfiguration.ExtractConfig.values()[nbt.getInt("config")];
		}
	}

	public enum ExtractConfig {
		NONE(false, false),
		INPUT(false, true),
		OUTPUT(true, false),
		ALL(true, true);

		public static final PacketCodec<ByteBuf, ExtractConfig> PACKET_CODEC = PacketCodecs.INTEGER
			.xmap(integer -> ExtractConfig.values()[integer], Enum::ordinal);

		boolean extract;
		boolean insert;

		ExtractConfig(boolean extract, boolean insert) {
			this.extract = extract;
			this.insert = insert;
		}

		public boolean isExtract() {
			return extract;
		}

		public boolean isInsert() {
			return insert;
		}

		public boolean isEnabled() {
			return extract || insert;
		}

		public FluidConfiguration.ExtractConfig getNext() {
			int i = this.ordinal() + 1;
			if (i >= FluidConfiguration.ExtractConfig.values().length) {
				i = 0;
			}
			return FluidConfiguration.ExtractConfig.values()[i];
		}
	}
}
