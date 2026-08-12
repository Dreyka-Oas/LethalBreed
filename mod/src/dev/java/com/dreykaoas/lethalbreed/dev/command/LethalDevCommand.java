package com.dreykaoas.lethalbreed.dev.command;

import com.dreykaoas.lethalbreed.dev.DevBootstrap;
import com.dreykaoas.lethalbreed.dev.contam.DevContam;

import com.dreykaoas.lethalbreed.config.domain.ContaminationConfig;
import com.dreykaoas.lethalbreed.effect.ContaminationManager;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * {@code /lethaldev …} — a dev-only Swiss-army command for exercising the mod's slow effects on demand instead
 * of waiting out their real timers. Registered ONLY from {@link DevBootstrap} (dev source set), so it is never
 * present in a shipped player jar.
 *
 * <p>Subcommands (all operate on the entity the player is looking at, else the player itself):
 * <ul>
 *   <li>{@code contaminate} — infect now (starts the latent stage).</li>
 *   <li>{@code symptoms} — force the symptomatic stage now (skips the 5–10 in-game-day roll).</li>
 *   <li>{@code level <1..5>} — jump the target straight to a plague level (infects + surfaces symptoms first if
 *       needed); rerolls its per-victim intensity for that level.</li>
 *   <li>{@code cure} — clear the plague outright.</li>
 *   <li>{@code status} — report the plague stage of the target.</li>
 *   <li>{@code timescale [factor]} — read/set the plague time-compression factor (e.g. 2 = twice as fast,
 *       so pulses and symptom rolls fire in half the time). {@code 1} restores real timing.</li>
 * </ul>
 * Op-gated (level 2) as a defence-in-depth alongside the dev-env-only registration.
 */
public final class LethalDevCommand {
    private LethalDevCommand() {}

    /** Range (blocks) of the "what am I looking at" raycast used to pick a target. */
    private static final double LOOK_REACH = 24.0;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("lethaldev")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("contaminate").executes(LethalDevCommand::contaminate))
                .then(Commands.literal("symptoms").executes(LethalDevCommand::symptoms))
                .then(Commands.literal("level")
                        .then(Commands.argument("n", IntegerArgumentType.integer(1, 5))
                                .executes(ctx -> setLevel(ctx, IntegerArgumentType.getInteger(ctx, "n")))))
                .then(Commands.literal("cure").executes(LethalDevCommand::cure))
                .then(Commands.literal("status").executes(LethalDevCommand::status))
                .then(Commands.literal("timescale")
                        .executes(LethalDevCommand::showTimescale)
                        .then(Commands.argument("factor", DoubleArgumentType.doubleArg(0.001, 10000.0))
                                .executes(ctx -> setTimescale(ctx,
                                        DoubleArgumentType.getDouble(ctx, "factor"))))));
    }

    private static int contaminate(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        LivingEntity target = target(ctx);
        ContaminationManager.contaminate(target);
        boolean ok = ContaminationManager.isContaminated(target);
        reply(ctx, ok ? ChatFormatting.GREEN : ChatFormatting.YELLOW,
                ok ? "contaminated " + name(target) + " (latent)"
                        : "could not contaminate " + name(target) + " (zombie / already infected / disabled)");
        return ok ? 1 : 0;
    }

    private static int symptoms(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        LivingEntity target = target(ctx);
        if (!ContaminationManager.isContaminated(target)) {
            ContaminationManager.contaminate(target); // convenience: infect first so "symptoms" always works
        }
        DevContam.forceSymptomatic(target);
        reply(ctx, ChatFormatting.RED, "forced symptoms on " + name(target));
        return 1;
    }

    private static int setLevel(CommandContext<CommandSourceStack> ctx, int n) throws CommandSyntaxException {
        LivingEntity target = target(ctx);
        DevContam.forceLevel(target, n);
        int got = ContaminationManager.plagueLevel(target);
        reply(ctx, got > 0 ? ChatFormatting.RED : ChatFormatting.YELLOW,
                got > 0 ? "set " + name(target) + " to plague level " + got
                        : "could not set level on " + name(target) + " (zombie / disabled)");
        return got > 0 ? 1 : 0;
    }

    private static int cure(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        LivingEntity target = target(ctx);
        ContaminationManager.clearPlague(target);
        reply(ctx, ChatFormatting.AQUA, "cleared plague from " + name(target));
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        LivingEntity target = target(ctx);
        String stage = !ContaminationManager.isContaminated(target) ? "clean"
                : ContaminationManager.isSymptomatic(target) ? "symptomatic" : "latent";
        int lvl = ContaminationManager.plagueLevel(target);
        reply(ctx, ChatFormatting.GRAY, name(target) + " — plague: " + stage
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

    /** The entity the player is looking at within {@link #LOOK_REACH}, or the player itself as a fallback. */
    private static LivingEntity target(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        Vec3 end = eye.add(look.scale(LOOK_REACH));
        AABB box = player.getBoundingBox().expandTowards(look.scale(LOOK_REACH)).inflate(1.0);
        LivingEntity best = null;
        double bestT = Double.MAX_VALUE;
        List<Entity> candidates = player.level().getEntities(player, box,
                e -> e instanceof LivingEntity && e.isAlive() && e.isPickable());
        for (Entity e : candidates) {
            var clip = e.getBoundingBox().inflate(0.3).clip(eye, end);
            if (clip.isPresent()) {
                double t = clip.get().distanceToSqr(eye);
                if (t < bestT) {
                    bestT = t;
                    best = (LivingEntity) e;
                }
            }
        }
        return best != null ? best : player;
    }

    private static String name(LivingEntity e) {
        return e instanceof ServerPlayer p ? p.getGameProfile().name() : e.getType().toString();
    }

    private static void reply(CommandContext<CommandSourceStack> ctx, ChatFormatting color, String msg) {
        ctx.getSource().sendSuccess(
                () -> Component.literal("[LethalDev] " + msg).withStyle(color), false);
    }
}
