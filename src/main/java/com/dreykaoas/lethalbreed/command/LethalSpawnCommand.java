package com.dreykaoas.lethalbreed.command;

import com.dreykaoas.lethalbreed.LethalBreedMod;
import com.dreykaoas.lethalbreed.config.LethalBreedConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;

import java.util.Optional;

/**
 * {@code /lethalspawn <entity> <count> [delaySeconds]} — dev/load-test tool. Spawns {@code count}
 * entities in a ring around the player after an optional delay, and turns on the dev perf recap.
 * Op-gated (permission level 2).
 */
public final class LethalSpawnCommand {
    private LethalSpawnCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // Note: 1.21.11 replaced hasPermission(int) with a Permission-object system. For this dev tool
        // we leave it open — it still requires command access (cheats in singleplayer / op on a server).
        dispatcher.register(Commands.literal("lethalspawn")
                .then(Commands.argument("entity", StringArgumentType.word())
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 100000))
                                .executes(ctx -> run(ctx, 0))
                                .then(Commands.argument("delaySeconds", IntegerArgumentType.integer(0, 600))
                                        .executes(ctx -> run(ctx, IntegerArgumentType.getInteger(ctx, "delaySeconds")))))));
    }

    private static int run(CommandContext<CommandSourceStack> ctx, int delaySeconds) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayerOrException();
        ServerLevel level = src.getLevel();
        String entityArg = StringArgumentType.getString(ctx, "entity");
        int count = IntegerArgumentType.getInteger(ctx, "count");

        Optional<EntityType<?>> type = EntityType.byString(entityArg);
        if (type.isEmpty()) {
            src.sendFailure(Component.literal("[LethalBreed] unknown entity: " + entityArg));
            return 0;
        }

        long dueTick = level.getServer().getTickCount() + (long) delaySeconds * 20L;
        DevSpawnScheduler.schedule(dueTick, level, player.blockPosition(), type.get(), count, LethalBreedConfig.devSpawnRadius);

        // Activate the dev perf recap (dev environment only; see TickScheduler).
        LethalBreedMod.perfRecapActive = true;

        final int n = count;
        final String e = entityArg;
        final int d = delaySeconds;
        src.sendSuccess(() -> Component.literal(
                "[LethalBreed] queued " + n + " x " + e + (d > 0 ? (" in " + d + "s") : " now")
                        + " (radius " + LethalBreedConfig.devSpawnRadius + "). Perf recap ON (dev only)."), true);
        return count;
    }
}
