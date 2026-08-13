package com.dreykaoas.lethalbreed.dev.command;

import com.dreykaoas.lethalbreed.dev.DevBootstrap;
import com.dreykaoas.lethalbreed.dev.contam.DevContam;

import com.dreykaoas.lethalbreed.config.domain.ContaminationConfig;
import com.dreykaoas.lethalbreed.effect.ContaminationManager;

import com.dreykaoas.lethalbreed.command.LookTarget;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;

/**
 * The dev-only half of {@code /lethaldev …} — a Swiss-army command for exercising the mod's slow effects on
 * demand instead of waiting out their real timers. Registered ONLY from {@link DevBootstrap} (dev source set),
 * so none of these subcommands is present in a shipped player jar.
 *
 * <p>The {@code lethaldev} literal itself, and its {@code level <n>} and {@code cure} subcommands, are
 * registered by {@code PlagueCommand} in src/main and DO ship. Brigadier merges two registrations of the same
 * literal, so this class adds its branches onto that node rather than creating a second one — which is why
 * it must not re-declare {@code level} or {@code cure}, and why its {@code requires} gate has to stay at the same
 * permission level as the shipped one (a merge keeps the first node's requirement).
 *
 * <p>Subcommands added here (all operate on the entity the player is looking at, else the player itself):
 * <ul>
 *   <li>{@code contaminate} — infect now (starts the latent stage).</li>
 *   <li>{@code symptoms} — force the symptomatic stage now (skips the 5–10 in-game-day roll).</li>
 *   <li>{@code status} — report the plague stage of the target.</li>
 *   <li>{@code timescale [factor]} — read/set the plague time-compression factor (e.g. 2 = twice as fast,
 *       so pulses and symptom rolls fire in half the time). {@code 1} restores real timing.</li>
 * </ul>
 * Op-gated (level 2) as a defence-in-depth alongside the dev-env-only registration.
 */
public final class LethalDevCommand {
    private LethalDevCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("lethaldev")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("contaminate").executes(LethalDevCommand::contaminate))
                .then(Commands.literal("symptoms").executes(LethalDevCommand::symptoms))
                .then(Commands.literal("status").executes(LethalDevCommand::status))
                .then(Commands.literal("timescale")
                        .executes(LethalDevCommand::showTimescale)
                        .then(Commands.argument("factor", DoubleArgumentType.doubleArg(0.001, 10000.0))
                                .executes(ctx -> setTimescale(ctx,
                                        DoubleArgumentType.getDouble(ctx, "factor"))))));
    }

    private static int contaminate(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        LivingEntity target = LookTarget.of(ctx);
        ContaminationManager.contaminate(target);
        boolean ok = ContaminationManager.isContaminated(target);
        reply(ctx, ok ? ChatFormatting.GREEN : ChatFormatting.YELLOW,
                ok ? "contaminated " + LookTarget.name(target) + " (latent)"
                        : "could not contaminate " + LookTarget.name(target) + " (zombie / already infected / disabled)");
        return ok ? 1 : 0;
    }

    private static int symptoms(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        LivingEntity target = LookTarget.of(ctx);
        if (!ContaminationManager.isContaminated(target)) {
            ContaminationManager.contaminate(target); // convenience: infect first so "symptoms" always works
        }
        DevContam.forceSymptomatic(target);
        reply(ctx, ChatFormatting.RED, "forced symptoms on " + LookTarget.name(target));
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        LivingEntity target = LookTarget.of(ctx);
        String stage = !ContaminationManager.isContaminated(target) ? "clean"
                : ContaminationManager.isSymptomatic(target) ? "symptomatic" : "latent";
        int lvl = ContaminationManager.plagueLevel(target);
        reply(ctx, ChatFormatting.GRAY, LookTarget.name(target) + " — plague: " + stage
                + (lvl > 0 ? " (level " + lvl + ")" : "")
                + " | timescale x" + ContaminationConfig.contamDevTimeScale);
        return 1;
    }

    private static int showTimescale(CommandContext<CommandSourceStack> ctx) {
        reply(ctx, ChatFormatting.GRAY, "plague timescale = x" + ContaminationConfig.contamDevTimeScale);
        return 1;
    }

    private static int setTimescale(CommandContext<CommandSourceStack> ctx, double factor) {
        ContaminationConfig.contamDevTimeScale = factor;
        reply(ctx, ChatFormatting.GREEN, "plague timescale set to x" + factor
                + " (affects newly-scheduled pulses/rolls)");
        return 1;
    }

    private static void reply(CommandContext<CommandSourceStack> ctx, ChatFormatting color, String msg) {
        ctx.getSource().sendSuccess(
                () -> Component.literal("[LethalDev] " + msg).withStyle(color), false);
    }
}
