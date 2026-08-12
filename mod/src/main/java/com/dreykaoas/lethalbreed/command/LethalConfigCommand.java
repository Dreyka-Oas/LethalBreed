package com.dreykaoas.lethalbreed.command;

import com.dreykaoas.lethalbreed.config.io.ConfigIo;
import com.dreykaoas.lethalbreed.config.io.ConfigStructure;

import com.dreykaoas.lethalbreed.config.schema.ConfigFields;
import com.dreykaoas.lethalbreed.net.LethalConfigPayloads;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;

/**
 * {@code /lethalconfig} — the mod's single shipped, user-facing command.
 *
 * <ul>
 *   <li>{@code /lethalconfig}          — open the in-game GUI menu (player only; console falls back to
 *       the text dump)</li>
 *   <li>{@code /lethalconfig verify}   — report the config file's structural health</li>
 * </ul>
 *
 * The GUI (and its console fallback) is driven by reflection over EVERY field in the config
 * ({@link ConfigFields}), so any new option is exposed automatically. Editing an option is done through
 * the GUI, which round-trips over the {@code SetConfig} C2S packet, not through a text subcommand.
 *
 * Op-gated (permission level 2 / GAMEMASTERS): config changes are global (the config is static) and
 * persisted to {@code config/oas/lethalbreed.json}, so editing is restricted to operators (in singleplayer
 * this means "allow cheats"). The C2S {@code SetConfig} packet is gated identically server-side.
 */
public final class LethalConfigCommand {
    private LethalConfigCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("lethalconfig")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(LethalConfigCommand::openMenu)
                .then(Commands.literal("verify").executes(LethalConfigCommand::verify)));
    }

    private static int openMenu(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            return list(ctx); // console: fall back to the text dump
        }
        ServerPlayNetworking.send(player, new LethalConfigPayloads.OpenConfig(
                "@gpu=" + gpuInfo() + "\n" + ConfigFields.encodeSnapshot()));
        return 1;
    }

    /** Human-readable detected GPU, shown live on the {@code useGpu} row in the GUI. Runs on the server
     *  thread, so it MUST NOT call {@code isAvailable()}: that takes the compute monitor (blocking behind an
     *  in-flight solve) and lazily triggers OpenCL init — a ~second-long clBuildProgram — on a box where the
     *  admin disabled the GPU precisely to avoid OpenCL. It reads the already-known state instead (audit #9). */
    private static String gpuInfo() {
        var gpu = com.dreykaoas.lethalbreed.ai.flowfield.gpu.GpuComputeManager.get();
        if (!com.dreykaoas.lethalbreed.config.domain.engine.FlowConfig.useGpu) {
            return "GPU désactivé (useGpu=false) — CPU multithread";
        }
        if (!gpu.isInitialized()) {
            return "GPU non initialisé — CPU multithread";
        }
        return gpu.isAvailableNonBlocking()
                ? gpu.deviceName() + " (OpenCL)"
                : "Aucun GPU — CPU multithread";
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        CommandFeedback.success(ctx.getSource(),
                ConfigFields.all().size() + " options", ChatFormatting.GOLD, false);
        for (Field f : ConfigFields.all()) {
            String line = "  " + f.getName() + " = " + ConfigFields.read(f) + "  (" + ConfigFields.kind(f) + ")";
            ctx.getSource().sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.GRAY), false);
        }
        return ConfigFields.all().size();
    }

    /** Report what the last config read made of the file's SHAPE — misspelled option names, options
     *  filed under two categories, categories that do not exist. Not values: those are the user's to
     *  choose, and an out-of-range one is clamped on the way in by design.
     *
     *  <p>This exists because a log line is close to worthless to a solo player, who never opens
     *  latest.log. The startup WARN is for dedicated-server admins; this is for everyone else. */
    private static int verify(CommandContext<CommandSourceStack> ctx) {
        ConfigStructure.Report report = ConfigIo.lastReport();
        if (report == null) {
            CommandFeedback.failure(ctx.getSource(),
                    "Config jamais lue depuis ce démarrage — rien à vérifier.");
            return 0;
        }
        if (report.clean()) {
            CommandFeedback.success(ctx.getSource(),
                    "Structure OK — " + report.recognised() + "/" + report.keysInFile()
                            + " options reconnues.", ChatFormatting.GREEN, false);
            return 1;
        }

        CommandFeedback.success(ctx.getSource(),
                report.problemCount() + " problème(s) de structure — " + report.recognised() + "/"
                        + report.keysInFile() + " options reconnues.", ChatFormatting.GOLD, false);
        for (ConfigStructure.Unknown u : report.unknown()) {
            String line = u.suggestion() != null
                    ? "  option inconnue '" + u.name() + "' — vouliez-vous '" + u.suggestion() + "' ?"
                    : "  option inconnue '" + u.name() + "'";
            ctx.getSource().sendSuccess(
                    () -> Component.literal(line).withStyle(ChatFormatting.RED), false);
        }
        for (String d : report.duplicated()) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "  '" + d + "' apparaît dans deux catégories — une seule copie est lue")
                    .withStyle(ChatFormatting.RED), false);
        }
        for (String c : report.bogusCategory()) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "  '" + c + "' n'est pas une catégorie — ses options seront déplacées")
                    .withStyle(ChatFormatting.RED), false);
        }
        if (!report.misplaced().isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "  " + report.misplaced().size() + " option(s) mal rangée(s) — corrigé "
                            + "automatiquement à la prochaine écriture")
                    .withStyle(ChatFormatting.GRAY), false);
        }
        return report.problemCount();
    }
}
