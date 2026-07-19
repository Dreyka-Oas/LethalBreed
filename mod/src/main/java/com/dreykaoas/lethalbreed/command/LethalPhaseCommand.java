package com.dreykaoas.lethalbreed.command;

import com.dreykaoas.lethalbreed.phase.PhaseConfig;
import com.dreykaoas.lethalbreed.phase.PhaseManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * {@code /lethalphase [n]} — show the current difficulty phase, or force it to {@code n} (unbounded) for
 * testing. Forcing broadcasts the phase to all players, same as an auto-advance, and ignores any
 * configured {@code phaseMax} ceiling (manual override is deliberate).
 */
public final class LethalPhaseCommand {
    private LethalPhaseCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("lethalphase")
                .executes(LethalPhaseCommand::show)
                .then(Commands.argument("n", IntegerArgumentType.integer(1))
                        .executes(ctx -> set(ctx, IntegerArgumentType.getInteger(ctx, "n")))));
    }

    private static int show(CommandContext<CommandSourceStack> ctx) {
        int p = PhaseManager.current();
        CommandFeedback.success(ctx.getSource(), PhaseConfig.def(p).name(), false);
        return p;
    }

    private static int set(CommandContext<CommandSourceStack> ctx, int n) {
        PhaseManager.get().setPhase(ctx.getSource().getServer(), n);
        return n;
    }
}
