package com.dreykaoas.lethalbreed.dev.command;

import com.dreykaoas.lethalbreed.phase.PhaseConfig;
import com.dreykaoas.lethalbreed.phase.PhaseManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * {@code /lethalphase [n]} — show the current difficulty phase, or force it to {@code n} for testing.
 * Forcing broadcasts the phase to all players, same as an auto-advance, and ignores any configured
 * {@code phaseMax} ceiling (manual override is deliberate).
 *
 * <p>Operator-gated, like {@code /lethalconfig} and the other {@code dev} commands. The phase drives a
 * per-chunk, per-tick spawn loop on the server thread ({@code SpawnFrequencyMixin}) and is persisted to
 * the save, so an arbitrary value from an unprivileged player is a denial of service that survives a
 * restart. {@code n} is bounded here as well; {@link PhaseManager#MAX_PHASE} enforces the same ceiling
 * on every other path into the phase.
 */
public final class LethalPhaseCommand {
    private LethalPhaseCommand() {}

    private static final String PREFIX = "[LethalPhase] ";

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("lethalphase")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(LethalPhaseCommand::show)
                .then(Commands.argument("n", IntegerArgumentType.integer(1, PhaseManager.MAX_PHASE))
                        .executes(ctx -> set(ctx, IntegerArgumentType.getInteger(ctx, "n")))));
    }

    private static int show(CommandContext<CommandSourceStack> ctx) {
        int p = PhaseManager.current();
        reply(ctx.getSource(), PhaseConfig.def(p).name());
        return p;
    }

    private static int set(CommandContext<CommandSourceStack> ctx, int n) {
        PhaseManager.get().setPhase(ctx.getSource().getServer(), n);
        return n;
    }

    /** Local stand-in for the shared {@code command.CommandFeedback}, which is package-private and
     *  unreachable from {@code dev.command}. Mirrors its success shape with its own prefix. */
    private static void reply(CommandSourceStack src, String msg) {
        src.sendSuccess(() -> Component.literal(PREFIX + msg), false);
    }
}
