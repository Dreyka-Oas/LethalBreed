package com.dreykaoas.lethalbreed.dev.command;

import com.dreykaoas.lethalbreed.GameState;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import com.dreykaoas.lethalbreed.special.SpecialRoller;
import com.dreykaoas.lethalbreed.special.SpecialType;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /lethalspecial <type> [count]} — spawn {@code count} zombies forced to a given special type around
 * the player, for testing each ability. The type argument suggests the available ids.
 *
 * <p>Operator-gated, for the same reason {@link LethalSpawnCommand} is: it spawns entities that the
 * mod marks persistent, so they never despawn on their own. The 200 cap is per invocation and there is no
 * global counter, which makes an ungated version an unbounded entity accumulator — and, in the
 * {@code bombeur} variant, a PvP weapon.
 */
public final class LethalSpecialCommand {
    private LethalSpecialCommand() {}

    private static final String PREFIX = "[LethalSpecial] ";

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("lethalspecial")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("type", StringArgumentType.word())
                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(typeIds(), b))
                        .executes(ctx -> run(ctx, 1))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 200))
                                .executes(ctx -> run(ctx, IntegerArgumentType.getInteger(ctx, "count"))))));
    }

    private static List<String> typeIds() {
        List<String> ids = new ArrayList<>();
        for (SpecialType t : SpecialType.values()) {
            if (t != SpecialType.NONE) {
                ids.add(t.id());
            }
        }
        return ids;
    }

    private static int run(CommandContext<CommandSourceStack> ctx, int count) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayerOrException();
        ServerLevel level = src.getLevel();
        String typeArg = StringArgumentType.getString(ctx, "type");
        SpecialType type = SpecialType.fromId(typeArg);
        if (type == SpecialType.NONE) {
            fail(src, "unknown special type: " + typeArg);
            return 0;
        }

        for (int i = 0; i < count; i++) {
            double ang = (Math.PI * 2 * i) / count;
            BlockPos pos = player.blockPosition().offset((int) Math.round(Math.cos(ang) * 3), 0,
                    (int) Math.round(Math.sin(ang) * 3));
            Zombie z = EntityType.ZOMBIE.spawn(level, pos, EntitySpawnReason.COMMAND);
            if (z == null) {
                continue;
            }
            SpecialRoller.assign(z, type); // overrides any random roll done at spawn
            SmartZombie sz = GameState.REGISTRY.get(z.getId());
            if (sz != null) {
                sz.pursuit().refreshSpecial();
            }
        }

        reply(src, "spawned " + count + " x " + type.frName(), true);
        return count;
    }

    /** Local stand-in for the shared {@code command.CommandFeedback}, which is package-private and
     *  unreachable from {@code dev.command}. Mirrors its success/failure shape with its own prefix. */
    private static void reply(CommandSourceStack src, String msg, boolean broadcast) {
        src.sendSuccess(() -> Component.literal(PREFIX + msg), broadcast);
    }

    private static void fail(CommandSourceStack src, String msg) {
        src.sendFailure(Component.literal(PREFIX + msg));
    }
}
