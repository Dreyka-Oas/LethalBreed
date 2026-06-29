package com.dreykaoas.lethalbreed.init;

import com.dreykaoas.lethalbreed.command.LethalConfigCommand;
import com.dreykaoas.lethalbreed.command.LethalPhaseCommand;
import com.dreykaoas.lethalbreed.command.LethalSpawnCommand;
import com.dreykaoas.lethalbreed.command.LethalSpecialCommand;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

/** Registers the mod's dev/load-test commands. */
public final class CommandInit {
    private CommandInit() {}

    public static void register() {
        // Dev/load-test command: /lethalspawn <entity> <count> [delaySeconds]; /lethalphase [n]; /lethalspecial
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LethalSpawnCommand.register(dispatcher, registryAccess);
            LethalPhaseCommand.register(dispatcher);
            LethalSpecialCommand.register(dispatcher);
            LethalConfigCommand.register(dispatcher);
        });
    }
}
