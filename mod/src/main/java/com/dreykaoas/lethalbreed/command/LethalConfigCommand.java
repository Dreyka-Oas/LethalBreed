package com.dreykaoas.lethalbreed.command;

import com.dreykaoas.lethalbreed.config.ConfigFields;
import com.dreykaoas.lethalbreed.config.ConfigIo;
import com.dreykaoas.lethalbreed.config.ConfigStructure;
import com.dreykaoas.lethalbreed.net.LethalConfigPayloads;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.util.Locale;

/**
 * {@code /lethalconfig} — runtime editor for EVERY field in the config, driven by reflection
 * ({@link ConfigFields}) so any new option is exposed automatically.
 *
 * <ul>
 *   <li>{@code /lethalconfig}                  — open the in-game GUI menu (player only)</li>
 *   <li>{@code /lethalconfig list}             — print every option + value to chat</li>
 *   <li>{@code /lethalconfig verify}           — report the config file's structural health</li>
 *   <li>{@code /lethalconfig get <field>}      — show one option</li>
 *   <li>{@code /lethalconfig set <field> <v>}  — change one option (persists to JSON)</li>
 *   <li>{@code /lethalconfig reset <field>}    — restore one option to default</li>
 *   <li>{@code /lethalconfig reset all}        — restore everything to defaults</li>
 * </ul>
 *
 * Op-gated (permission level 2 / GAMEMASTERS): config changes are global (the config is static) and
 * persisted to {@code config/oas/lethalbreed.json}, so editing is restricted to operators (in singleplayer
 * this means "allow cheats"). The C2S {@code SetConfig} packet is gated identically server-side.
 */
public final class LethalConfigCommand {
    private LethalConfigCommand() {}

    private static final SuggestionProvider<CommandSourceStack> FIELD_SUGGEST = (ctx, b) -> {
        String rem = b.getRemaining().toLowerCase(Locale.ROOT);
        for (Field f : ConfigFields.all()) {
            if (f.getName().toLowerCase(Locale.ROOT).startsWith(rem)) {
                b.suggest(f.getName());
            }
        }
        return b.buildFuture();
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("lethalconfig")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(LethalConfigCommand::openMenu)
                .then(Commands.literal("list").executes(LethalConfigCommand::list))
                .then(Commands.literal("verify").executes(LethalConfigCommand::verify))
                .then(Commands.literal("get")
                        .then(Commands.argument("field", StringArgumentType.word())
                                .suggests(FIELD_SUGGEST)
                                .executes(LethalConfigCommand::get)))
                .then(Commands.literal("set")
                        .then(Commands.argument("field", StringArgumentType.word())
                                .suggests(FIELD_SUGGEST)
                                .then(Commands.argument("value", StringArgumentType.greedyString())
                                        .executes(LethalConfigCommand::set))))
                .then(Commands.literal("reset")
                        .executes(LethalConfigCommand::resetAll)
                        .then(Commands.literal("all").executes(LethalConfigCommand::resetAll))
                        .then(Commands.argument("field", StringArgumentType.word())
                                .suggests(FIELD_SUGGEST)
                                .executes(LethalConfigCommand::reset))));
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
        if (!com.dreykaoas.lethalbreed.config.domain.FlowConfig.useGpu) {
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

    private static int get(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "field");
        Field f = ConfigFields.find(name);
        if (f == null) {
            return unknown(ctx, name);
        }
        CommandFeedback.success(ctx.getSource(),
                f.getName() + " = " + ConfigFields.read(f) + "  (default " + ConfigFields.defaultOf(f.getName()) + ")",
                false);
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "field");
        String raw = StringArgumentType.getString(ctx, "value").trim();
        Field f = ConfigFields.find(name);
        if (f == null) {
            return unknown(ctx, name);
        }
        if (!ConfigFields.apply(name, raw, true)) {
            CommandFeedback.failure(ctx.getSource(),
                    "bad value '" + raw + "' for " + ConfigFields.kind(f) + " " + name);
            return 0;
        }
        CommandFeedback.success(ctx.getSource(),
                name + " -> " + ConfigFields.read(f), ChatFormatting.GREEN, true);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "field");
        Field f = ConfigFields.find(name);
        if (f == null) {
            return unknown(ctx, name);
        }
        ConfigFields.apply(name, ConfigFields.defaultOf(name), true);
        CommandFeedback.success(ctx.getSource(),
                name + " reset to " + ConfigFields.read(f), ChatFormatting.YELLOW, true);
        return 1;
    }

    private static int resetAll(CommandContext<CommandSourceStack> ctx) {
        int n = ConfigFields.resetAll();
        CommandFeedback.success(ctx.getSource(),
                "reset " + n + " options to defaults", ChatFormatting.YELLOW, true);
        return n;
    }

    private static int unknown(CommandContext<CommandSourceStack> ctx, String name) {
        CommandFeedback.failure(ctx.getSource(),
                "unknown option '" + name + "' — /lethalconfig list");
        return 0;
    }
}
