package com.dreykaoas.lethalbreed.command;

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
 * {@code /lethalconfig} — the mod's single shipped, user-facing command: open the in-game GUI menu
 * (player only; a console sender falls back to the text dump).
 *
 * <p>It had a {@code verify} subcommand that printed the config file's structural health. It was
 * removed once the loader learned to repair drift by itself: what it reported is now either fixed
 * before anyone could read it, or stated in full by the operator join notice in {@code LifecycleInit}.
 * A command whose only job is to show the detail another message left out is a message that should
 * have carried its detail.
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
                .executes(LethalConfigCommand::openMenu));
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

}
