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

package techreborn.energybind;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.PosArgument;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import techreborn.blockentity.cable.CableBlockEntity;
import techreborn.blockentity.cable.CableNetwork;
import techreborn.blockentity.cable.CableNetworkManager;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * {@code /techreborn igi_energybind <x> <y> <z>} binds the cable at that
 * position so the InGame Info XML tags show its network.
 * <p>
 * Absolute coordinates only, resolved against the caller's dimension: the
 * numbers live on the server and are pushed to the binding player, so a
 * relative position would silently bind the wrong block once the player walks
 * away.
 */
public final class EnergyBindCommand {

	private EnergyBindCommand() {
	}

	public static LiteralArgumentBuilder<ServerCommandSource> build() {
		return literal("igi_energybind")
			.then(argument("pos", BlockPosArgumentType.blockPos())
				.executes(EnergyBindCommand::bind));
	}

	private static int bind(CommandContext<ServerCommandSource> context) {
		ServerPlayerEntity player = context.getSource().getPlayer();
		if (player == null) {
			context.getSource().sendError(Text.translatable("techreborn.energybind.player_only"));
			return 0;
		}

		// Reject relative coordinates: the binding outlives the player's position,
		// so "~ ~ ~" would silently point at whatever block they stood on.
		PosArgument posArgument = context.getArgument("pos", PosArgument.class);
		if (posArgument.isXRelative() || posArgument.isYRelative() || posArgument.isZRelative()) {
			context.getSource().sendError(Text.translatable("techreborn.energybind.absolute_only"));
			return 0;
		}

		BlockPos pos = BlockPosArgumentType.getBlockPos(context, "pos");
		if (!(player.getServerWorld().getBlockEntity(pos) instanceof CableBlockEntity cable)) {
			context.getSource().sendError(Text.translatable("techreborn.energybind.not_a_cable",
				pos.getX(), pos.getY(), pos.getZ()));
			return 0;
		}

		CableNetwork network = CableNetworkManager.networkOf(cable);
		if (network == null || network.size() == 0) {
			context.getSource().sendError(Text.translatable("techreborn.energybind.no_network"));
			return 0;
		}

		EnergyBindManager.bind(player, player.getServerWorld().getRegistryKey(), pos);
		context.getSource().sendFeedback(() -> Text.translatable("techreborn.energybind.bound",
			pos.getX(), pos.getY(), pos.getZ(), network.size()), false);
		return 1;
	}
}
