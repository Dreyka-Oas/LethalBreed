package com.dreykaoas.lethalbreed.command;

import com.dreykaoas.lethalbreed.entity.gecko.HorrorZombie;
import com.dreykaoas.lethalbreed.entity.gecko.LethalEntities;
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

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /lethalhorror [variant] [count]} — spawn {@code count} horror zombies of a given variant in a ring
 * around the player (variant defaults to {@code horror_zombie}). The variant argument suggests the roster ids.
 */
public final class LethalHorrorCommand {
    private LethalHorrorCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("lethalhorror")
                .executes(ctx -> run(ctx, "horror_zombie", 1))
                .then(Commands.argument("variant", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(ids(), b))
                        .executes(ctx -> run(ctx, StringArgumentType.getString(ctx, "variant"), 1))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 50))
                                .executes(ctx -> run(ctx, StringArgumentType.getString(ctx, "variant"),
                                        IntegerArgumentType.getInteger(ctx, "count"))))));
    }

    private static List<String> ids() {
        List<String> l = new ArrayList<>();
        for (LethalEntities.Variant v : LethalEntities.VARIANTS) {
            l.add(v.id());
        }
        return l;
    }

    private static int run(CommandContext<CommandSourceStack> ctx, String variant, int count) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayerOrException();
        ServerLevel level = src.getLevel();

        EntityType<HorrorZombie> type = null;
        for (LethalEntities.Variant v : LethalEntities.VARIANTS) {
            if (v.id().equals(variant)) {
                type = v.type();
            }
        }
        if (type == null) {
            CommandFeedback.failure(src, "unknown horror variant: " + variant + " (try: " + String.join(", ", ids()) + ")");
            return 0;
        }

        int spawned = 0;
        for (int i = 0; i < count; i++) {
            double ang = (Math.PI * 2 * i) / count;
            BlockPos pos = player.blockPosition().offset((int) Math.round(Math.cos(ang) * 3), 0,
                    (int) Math.round(Math.sin(ang) * 3));
            HorrorZombie z = type.spawn(level, pos, EntitySpawnReason.COMMAND);
            if (z != null) {
                spawned++;
            }
        }

        if (spawned == 0) {
            CommandFeedback.failure(src, "could not spawn any horror zombie here");
            return 0;
        }
        CommandFeedback.success(src, "spawned " + spawned + " x " + variant, true);
        return spawned;
    }
}
