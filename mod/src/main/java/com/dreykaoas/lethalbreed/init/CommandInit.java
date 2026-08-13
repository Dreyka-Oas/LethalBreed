package com.dreykaoas.lethalbreed.init;

import com.dreykaoas.lethalbreed.command.LethalConfigCommand;
import com.dreykaoas.lethalbreed.command.PlagueLevelCommand;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

/**
 * Registers the commands a player jar ships: {@code /lethalconfig} and {@code /lethaldev level <n>}.
 *
 * <p>Everything else ({@code /lethalspawn}, {@code /lethalphase}, {@code /lethalspecial}, and the other
 * five {@code /lethaldev} subcommands) is a developer tool living in the {@code dev} source set and
 * registered by {@code DevBootstrap}, so a player jar contains none of it. The dev registration reuses
 * the {@code lethaldev} literal registered here — Brigadier merges the two trees.
 */
public final class CommandInit {
    private CommandInit() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LethalConfigCommand.register(dispatcher);
            PlagueLevelCommand.register(dispatcher);
        });
    }
}
