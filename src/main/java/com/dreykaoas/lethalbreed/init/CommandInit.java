package com.dreykaoas.lethalbreed.init;

import com.dreykaoas.lethalbreed.command.LethalConfigCommand;
import com.dreykaoas.lethalbreed.command.LethalPhaseCommand;
import com.dreykaoas.lethalbreed.command.LethalSpecialCommand;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

/**
 * Registers the mod's user-facing commands ({@code /lethalphase}, {@code /lethalspecial},
 * {@code /lethalconfig}). The dev/load-test {@code /lethalspawn} command lives in the {@code dev} source set
 * and is registered by {@code DevBootstrap}, not here.
 */
public final class CommandInit {
    private CommandInit() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LethalPhaseCommand.register(dispatcher);
            LethalSpecialCommand.register(dispatcher);
            LethalConfigCommand.register(dispatcher);
        });
    }
}
