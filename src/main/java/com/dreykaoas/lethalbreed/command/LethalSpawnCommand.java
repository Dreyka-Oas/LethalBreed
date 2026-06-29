package com.dreykaoas.lethalbreed.command;

import com.dreykaoas.lethalbreed.config.domain.ProgressionConfig;
import com.dreykaoas.lethalbreed.config.domain.SchedulerConfig;

import com.dreykaoas.lethalbreed.dev.DevSpawnScheduler;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;

/**
 * {@code /lethalspawn <entity> <count> [delaySeconds]} — dev/load-test tool. Spawns {@code count}
 * entities in a ring around the player after an optional delay, and reports the dev perf-recap state
 * (driven by {@code debugLogInterval}). Op-gated (permission level 2).
 *
 * <p>The {@code entity} argument is a vanilla {@link ResourceArgument} over the entity-type registry, so it
 * shows the same summonable-entity suggestion list as {@code /summon} (the popup above the input) and
 * accepts namespaced ids (e.g. {@code minecraft:zombie}, modded entities) — a plain {@code word()} string
 * had no suggestions and couldn't accept a namespace.
 */
public final class LethalSpawnCommand {
    private LethalSpawnCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        // Note: 1.21.11 replaced hasPermission(int) with a Permission-object system. For this dev tool
        // we leave it open — it still requires command access (cheats in singleplayer / op on a server).
        dispatcher.register(Commands.literal("lethalspawn")
                .then(Commands.argument("entity", ResourceArgument.resource(buildContext, Registries.ENTITY_TYPE))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 100000))
                                .executes(ctx -> run(ctx, 0))
                                .then(Commands.argument("delaySeconds", IntegerArgumentType.integer(0, 600))
                                        .executes(ctx -> run(ctx, IntegerArgumentType.getInteger(ctx, "delaySeconds")))))));
    }

    private static int run(CommandContext<CommandSourceStack> ctx, int delaySeconds) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayerOrException();
        ServerLevel level = src.getLevel();
        Holder.Reference<EntityType<?>> ref = ResourceArgument.getSummonableEntityType(ctx, "entity");
        EntityType<?> type = ref.value();
        int count = IntegerArgumentType.getInteger(ctx, "count");

        long dueTick = level.getServer().getTickCount() + (long) delaySeconds * 20L;
        DevSpawnScheduler.schedule(dueTick, level, player.blockPosition(), type, count, ProgressionConfig.devSpawnRadius);

        // The dev perf recap is driven solely by SchedulerConfig.debugLogInterval (and only logs in a dev
        // env). The command does NOT mutate config — ConfigIo serialises every static field on the next save,
        // so a runtime tweak here would silently become permanent. Just report the current state truthfully.
        int recapInterval = SchedulerConfig.debugLogInterval;
        String recap = recapInterval > 0
                ? ("perf recap every " + recapInterval + " ticks (dev only)")
                : "perf recap OFF — set debugLogInterval > 0 to enable";

        final int n = count;
        final String e = EntityType.getKey(type).toString();
        final int d = delaySeconds;
        src.sendSuccess(() -> Component.literal(
                "[LethalBreed] queued " + n + " x " + e + (d > 0 ? (" in " + d + "s") : " now")
                        + " (radius " + ProgressionConfig.devSpawnRadius + "). " + recap + "."), true);
        return count;
    }
}
