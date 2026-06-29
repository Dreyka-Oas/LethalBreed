package com.dreykaoas.lethalbreed.command;

import com.dreykaoas.lethalbreed.phase.PhaseConfig;
import com.dreykaoas.lethalbreed.phase.PhaseManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * {@code /lethalphase [n]} — show the current difficulty phase, or force it to {@code n} (1..15) for
 * testing. Forcing broadcasts the phase to all players, same as an auto-advance.
 */
public final class LethalPhaseCommand {
    private LethalPhaseCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("lethalphase")
                .executes(LethalPhaseCommand::show)
                .then(Commands.argument("n", IntegerArgumentType.integer(1, PhaseConfig.count()))
                        .executes(ctx -> set(ctx, IntegerArgumentType.getInteger(ctx, "n")))));
    }

    private static int show(CommandContext<CommandSourceStack> ctx) {
        int p = PhaseManager.current();
        ctx.getSource().sendSuccess(
                () -> Component.literal("[LethalBreed] Phase " + p + " — " + PhaseConfig.def(p).name()), false);
        return p;
    }

    private static int set(CommandContext<CommandSourceStack> ctx, int n) {
        PhaseManager.get().setPhase(ctx.getSource().getServer(), n);
        return n;
    }
}
