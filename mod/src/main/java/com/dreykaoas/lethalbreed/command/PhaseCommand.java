package com.dreykaoas.lethalbreed.command;

import com.dreykaoas.lethalbreed.phase.PhaseConfig;
import com.dreykaoas.lethalbreed.phase.PhaseManager;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * {@code /lethalphase} — tell the sender which difficulty phase the world is on, and nothing else.
 *
 * <p>Ungated on purpose, unlike every other command here. It only reads, and the phase is not a secret:
 * {@code PhaseManager.broadcast} already announces each advance to every player in the server. Requiring
 * operator rights to look up a number the game shouts at you would be theatre.
 *
 * <p>The reply goes to the sender alone ({@code sendSuccess(…, false)}) — asking what phase it is must not
 * spam the other players.
 *
 * <p>Forcing the phase ({@code /lethalphase <n>}) is a separate branch registered from {@code src/dev} by
 * {@code LethalPhaseCommand} and never shipped. That branch carries its OWN operator gate rather than
 * relying on this literal: Brigadier keeps the FIRST registration's {@code requires()} when it merges two
 * registrations of one literal, so the ungated node below would otherwise hand the forcing branch to every
 * player in the dev environment. {@code LiteralMergeTest} pins both halves of that rule.
 */
public final class PhaseCommand {
    private PhaseCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("lethalphase").executes(PhaseCommand::show));
    }

    private static int show(CommandContext<CommandSourceStack> ctx) {
        int p = PhaseManager.current();
        CommandFeedback.success(ctx.getSource(),
                "Phase " + p + " — " + PhaseConfig.def(p).name(), ChatFormatting.GOLD, false);
        return p;
    }
}
