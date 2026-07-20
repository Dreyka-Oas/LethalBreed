package com.dreykaoas.lethalbreed.command;

import com.dreykaoas.lethalbreed.entity.HorrorModelAttachment;
import com.dreykaoas.lethalbreed.entity.HorrorModels;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * {@code /lethalhorror <model> [count]} — spawn {@code count} plain zombies in a ring around the player, each
 * FORCED to wear a given render model (one of {@link HorrorModels#IDS}). These are ordinary {@code
 * minecraft:zombie}s; only their {@link HorrorModelAttachment#MODEL} differs. The model attachment is set
 * AFTER spawn on purpose — {@code finalizeSpawn} rolls a random model, so we overwrite it here to pin the one
 * you asked for. Handy for reviewing each of the 15 horror models on demand.
 */
public final class LethalHorrorCommand {
    private LethalHorrorCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("lethalhorror")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("model", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(HorrorModels.IDS, b))
                        .executes(ctx -> run(ctx, StringArgumentType.getString(ctx, "model"), 1))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 50))
                                .executes(ctx -> run(ctx, StringArgumentType.getString(ctx, "model"),
                                        IntegerArgumentType.getInteger(ctx, "count"))))));
    }

    private static int run(CommandContext<CommandSourceStack> ctx, String model, int count) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayerOrException();
        ServerLevel level = src.getLevel();

        int index = HorrorModels.indexOf(model);
        if (index < 0) {
            CommandFeedback.failure(src, "unknown model: " + model + " (try: " + String.join(", ", HorrorModels.IDS) + ")");
            return 0;
        }

        int spawned = 0;
        for (int i = 0; i < count; i++) {
            double ang = (Math.PI * 2 * i) / Math.max(1, count);
            BlockPos pos = player.blockPosition().offset((int) Math.round(Math.cos(ang) * 3), 0,
                    (int) Math.round(Math.sin(ang) * 3));
            Zombie z = EntityType.ZOMBIE.spawn(level, pos, EntitySpawnReason.COMMAND);
            if (z != null) {
                z.setAttached(HorrorModelAttachment.MODEL, index); // pin the requested model (overrides the spawn roll)
                spawned++;
            }
        }

        if (spawned == 0) {
            CommandFeedback.failure(src, "could not spawn any zombie here");
            return 0;
        }
        CommandFeedback.success(src, "spawned " + spawned + " zombie(s) with model '" + model + "'", true);
        return spawned;
    }
}
