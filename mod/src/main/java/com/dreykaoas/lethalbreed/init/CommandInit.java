package com.dreykaoas.lethalbreed.init;

import com.dreykaoas.lethalbreed.command.LethalConfigCommand;
import com.dreykaoas.lethalbreed.command.PhaseCommand;
import com.dreykaoas.lethalbreed.command.PlagueCommand;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

/**
 * Registers the commands a player jar ships: {@code /lethalconfig}, {@code /lethaldev level <n>},
 * {@code /lethaldev cure} and the bare {@code /lethalphase} readout.
 *
 * <p>Everything else ({@code /lethalspawn}, {@code /lethalspecial} and the other four {@code /lethaldev}
 * subcommands) is a developer tool living in the {@code dev} source set and registered by
 * {@code DevBootstrap}, so a player jar contains none of it. Those dev subcommands attach to the
 * {@code lethaldev} literal registered here — Brigadier merges the two trees.
 */
public final class CommandInit {
    private CommandInit() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LethalConfigCommand.register(dispatcher);
            PlagueCommand.register(dispatcher);
            PhaseCommand.register(dispatcher);
        });
    }
}
