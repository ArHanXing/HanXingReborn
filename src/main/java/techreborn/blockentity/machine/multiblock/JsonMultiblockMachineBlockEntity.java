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

package techreborn.blockentity.machine.multiblock;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.math.BlockPos;
import reborncore.common.blockentity.MultiblockWriter;
import techreborn.TechReborn;
import techreborn.blockentity.machine.GenericMachineBlockEntity;
import techreborn.multiblock.MultiblockDefinition;
import techreborn.multiblock.MultiblockDefinitionLoader;

/**
 * Base class for multiblock machines whose structure is driven by a JSON
 * definition instead of hard-coded code.
 * <p>
 * Subclasses only need to implement {@link #getMultiblockId()}; the actual
 * structure is looked up from {@link MultiblockDefinitionLoader} (see
 * {@code config/techreborn/multiblock/}).
 */
public abstract class JsonMultiblockMachineBlockEntity extends GenericMachineBlockEntity {

	public JsonMultiblockMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, String name,
			int maxInput, int maxEnergy, Block toolDrop, int energySlot) {
		super(type, pos, state, name, maxInput, maxEnergy, toolDrop, energySlot);
	}

	/**
	 * @return {@link String} the id used to look up this machine's JSON
	 *         structure definition (matches the file name in
	 *         {@code config/techreborn/multiblock/}).
	 */
	public abstract String getMultiblockId();

	@Override
	public void writeMultiblock(MultiblockWriter writer) {
		MultiblockDefinition definition = MultiblockDefinitionLoader.get(getMultiblockId());
		if (definition == null) {
			TechReborn.LOGGER.warn("No multiblock definition found for '{}', machine cannot be assembled",
					getMultiblockId());
			return;
		}
		definition.apply(writer);
	}
}
