package com.dreykaoas.lethalbreed.dev.command;

import com.dreykaoas.lethalbreed.phase.PhaseManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * {@code /lethalphase <n>} — force the difficulty phase, for testing. Dev-only: registered from
 * {@code DevBootstrap}, so a player jar has this branch nowhere.
 *
 * <p>The bare {@code /lethalphase} readout is a separate, shipped command ({@code command.PhaseCommand}),
 * and it registers the literal this branch attaches to. Brigadier merges two registrations of one literal.
 *
 * <p>The operator gate sits on the {@code n} argument node, NOT on the literal, and that placement is
 * load-bearing: a merge keeps the FIRST registration's {@code requires()}, and the first registration is
 * the deliberately ungated readout. Gating the literal here would be silently discarded, handing phase
 * forcing to every player in the dev environment. {@code LiteralMergeTest} pins the rule.
 *
 * <p>Forcing broadcasts the phase to all players, same as an auto-advance, and ignores any configured
 * {@code phaseMax} ceiling (manual override is deliberate). The phase drives a per-chunk, per-tick spawn
 * loop on the server thread ({@code SpawnFrequencyMixin}) and is persisted to the save, so an arbitrary
 * value from an unprivileged player is a denial of service that survives a restart. {@code n} is bounded
 * here as well; {@link PhaseManager#MAX_PHASE} enforces the same ceiling on every other path into it.
 */
public final class LethalPhaseCommand {
    private LethalPhaseCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("lethalphase")
                .then(Commands.argument("n", IntegerArgumentType.integer(1, PhaseManager.MAX_PHASE))
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> set(ctx, IntegerArgumentType.getInteger(ctx, "n")))));
    }

    private static int set(CommandContext<CommandSourceStack> ctx, int n) {
        // No reply: setPhase broadcasts "☠ Phase n" to everyone, the sender included.
        PhaseManager.get().setPhase(ctx.getSource().getServer(), n);
        return n;
    }
}
