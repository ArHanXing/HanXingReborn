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

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import techreborn.space.data.PlanetCatalog;
import techreborn.team.TeamScopes;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Explicit operator-only test/recovery interface, separate from normal GUI packets. */
public final class SpaceProgressCommand {
	private SpaceProgressCommand() {
	}

	public static LiteralArgumentBuilder<ServerCommandSource> build() {
		return literal("space").requires(source -> source.hasPermissionLevel(2))
				.then(literal("progress").then(argument("player", EntityArgumentType.player())
						.then(argument("body", IdentifierArgumentType.identifier()).suggests((context, builder) -> {
							PlanetCatalog.definitions().keySet().forEach(id -> builder.suggest(id.toString()));
							return builder.buildFuture();
						}).then(argument("stage", IntegerArgumentType.integer(1, 3)).executes(context -> {
							var player = EntityArgumentType.getPlayer(context, "player");
							var id = IdentifierArgumentType.getIdentifier(context, "body");
							var body = PlanetCatalog.get(id);
							int stage = IntegerArgumentType.getInteger(context, "stage");
							if (body == null || body.type().equals("star")
									|| stage >= 2 && !body.missions().land().enabled()
									|| stage >= 3 && !body.missions().outpost().enabled()
									|| !body.missions().survey().enabled()) {
								context.getSource().sendError(Text.translatable("gui.techreborn.space.invalid_selection"));
								return 0;
							}
							boolean changed = SpaceProgressState.advance(player.getServerWorld(), TeamScopes.scopeOf(player), id, stage);
							context.getSource().sendFeedback(() -> Text.translatable("techreborn.space.progress_set", player.getName(), id.toString(), stage), true);
							return changed ? 1 : 0;
						})))));
	}
}
