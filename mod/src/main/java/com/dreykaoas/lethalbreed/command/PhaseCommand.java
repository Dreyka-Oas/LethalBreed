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
 * <p>There is no {@code /lethalphase <n>} any more, in either source set. Forcing the phase was deleted
 * rather than moved: every dev harness that needs a phase sets it through {@code PhaseManager.setPhase}
 * directly, so the command duplicated an API the tests already call.
 *
 * <p>Because this literal is ungated and registered first, any branch ever added to it — from either
 * source set — must carry its own {@code requires()}: Brigadier keeps the FIRST registration's when it
 * merges, so a gate declared on a second registration of {@code lethalphase} would be silently dropped.
 */
public final class PhaseCommand {
    private PhaseCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("lethalphase").executes(PhaseCommand::show));
    }

    private static int show(CommandContext<CommandSourceStack> ctx) {
        int p = PhaseManager.current();
        // The name alone, not "Phase p — <name>": PhaseDef.name is built as "Phase " + p, so spelling the
        // number out as well printed "Phase 1 — Phase 1". Reading it from the def rather than formatting
        // the number here keeps the command correct if phases ever get real names.
        CommandFeedback.success(ctx.getSource(),
                PhaseConfig.def(p).name(), ChatFormatting.GOLD, false);
        return p;
    }
}
