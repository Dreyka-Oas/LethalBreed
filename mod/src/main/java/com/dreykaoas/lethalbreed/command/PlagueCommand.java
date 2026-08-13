package com.dreykaoas.lethalbreed.command;

import com.dreykaoas.lethalbreed.effect.ContaminationManager;
import com.dreykaoas.lethalbreed.effect.contamination.ContaminationLifecycle;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.world.entity.LivingEntity;

/**
 * The shipped half of {@code /lethaldev …} — the two plague subcommands a player jar carries:
 *
 * <ul>
 *   <li>{@code level <n>} — jump the target straight to a plague level, instead of waiting out the 5–10
 *       in-game-day symptom roll and the 1–2 day climb per level.</li>
 *   <li>{@code cure} — clear the plague outright.</li>
 * </ul>
 *
 * <p>Both act on the entity you are looking at, else yourself ({@link LookTarget}).
 *
 * <p>The other four subcommands (contaminate, symptoms, status, timescale) stay in {@code src/dev} and are
 * added to this same {@code lethaldev} literal by {@code DevBootstrap}: Brigadier merges two registrations
 * of one literal, so the dev environment sees the full tree and a player sees these two branches alone.
 * {@code LiteralMergeTest} pins that contract — including the fact that a merge keeps the FIRST
 * registration's {@code requires()}, which is why both halves gate at the same permission level.
 *
 * <p>{@code level} is a jump, not a lock — see {@link ContaminationLifecycle#forceLevel}. The victim
 * rejoins the normal evolve roll and keeps climbing toward {@code contamMaxLevel}.
 *
 * <p>Op-gated (permission level 2 / GAMEMASTERS), like {@code /lethalconfig}: these mutate another
 * entity's state, and in singleplayer that means "allow cheats".
 */
public final class PlagueCommand {
    private PlagueCommand() {}

    /** The upper bound the argument accepts. {@code contamMaxLevel} is configurable and may be lower, in
     *  which case {@code setLevel} clamps and the feedback below reports the level actually reached —
     *  Brigadier bounds are fixed at registration, so they cannot track a config value that is not yet
     *  loaded when the command tree is built. */
    private static final int MAX_ARG = 5;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("lethaldev")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("level")
                        .then(Commands.argument("n", IntegerArgumentType.integer(1, MAX_ARG))
                                .executes(ctx -> setLevel(ctx, IntegerArgumentType.getInteger(ctx, "n")))))
                .then(Commands.literal("cure").executes(PlagueCommand::cure)));
    }

    private static int setLevel(CommandContext<CommandSourceStack> ctx, int n) throws CommandSyntaxException {
        LivingEntity target = LookTarget.of(ctx);
        ContaminationLifecycle.forceLevel(target, n);
        int got = ContaminationManager.plagueLevel(target);
        if (got <= 0) {
            CommandFeedback.failure(ctx.getSource(), LookTarget.name(target)
                    + " ne peut pas être infecté (zombie, ou contamination désactivée).");
            return 0;
        }
        // Report what was reached rather than what was asked: contamMaxLevel may have clamped it.
        CommandFeedback.success(ctx.getSource(),
                LookTarget.name(target) + " passe au niveau de peste " + got
                        + (got != n ? " (plafonné par contamMaxLevel)" : ""),
                ChatFormatting.RED, false);
        return got;
    }

    private static int cure(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        LivingEntity target = LookTarget.of(ctx);
        // Deliberately unconditional: clearing a clean victim is a no-op, and reporting "not infected" as a
        // failure would make the command useless for the one thing it is for — being sure it is gone.
        ContaminationManager.clearPlague(target);
        CommandFeedback.success(ctx.getSource(),
                "peste retirée de " + LookTarget.name(target), ChatFormatting.AQUA, false);
        return 1;
    }
}
