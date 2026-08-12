package com.dreykaoas.lethalbreed.init;

import com.dreykaoas.lethalbreed.command.LethalConfigCommand;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

/**
 * Registers the mod's single user-facing command, {@code /lethalconfig}. Every other command
 * ({@code /lethaldev}, {@code /lethalspawn}, {@code /lethalphase}, {@code /lethalspecial}) is a
 * developer tool: it lives in the {@code dev} source set and is registered by {@code DevBootstrap},
 * so a player jar contains none of them.
 */
public final class CommandInit {
    private CommandInit() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LethalConfigCommand.register(dispatcher);
        });
    }
}
