package com.dreykaoas.lethalbreed.dev;

import com.dreykaoas.lethalbreed.LethalBreed;
import com.dreykaoas.lethalbreed.ai.flowfield.ComputeSelfTest;
import com.dreykaoas.lethalbreed.config.domain.ProgressionConfig;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/**
 * Single entry point for every development-only feature (headless test harnesses + the {@code /lethalspawn}
 * load-test command). Lives in the {@code dev} source set, so it is compiled and packaged ONLY for
 * {@code runClient}/{@code runServer} — a shipped/remapped jar never contains this class or anything under
 * {@code com.dreykaoas.lethalbreed.dev}.
 *
 * <p>{@code main} never imports this class directly; it is loaded by reflection from
 * {@code LethalBreedMod#installDevHooks} and only when {@code FabricLoader.isDevelopmentEnvironment()} is
 * true. On a production jar the reflective lookup simply fails ({@link ClassNotFoundException}) and is
 * swallowed, so no dev wiring ever runs.
 */
public final class DevBootstrap {
    private DevBootstrap() {}

    /**
     * Wire every dev hook. Called reflectively by the main mod initializer (dev env only). The signature is
     * a stable, argument-free contract so the reflective call site in {@code main} needs no knowledge of the
     * dev classes.
     */
    public static void install() {
        // Headless verification arenas, driven per server tick. Each harness self-gates on its own
        // ProgressionConfig flag (devSpecialTest / devMechTest) AND the dev-env check, so registering the
        // tick listener here is harmless when the flags are off.
        ServerTickEvents.END_SERVER_TICK.register(SpecialTestHarness::onTick);
        ServerTickEvents.END_SERVER_TICK.register(MechanicsTestHarness::onTick);
        // Load-test spawn queue, drained each server tick (no-op unless /lethalspawn queued work).
        ServerTickEvents.END_SERVER_TICK.register(DevSpawnScheduler::tick);

        // Dev arenas build blocks at overlapping coordinates (special + mechanics share Y=101 with
        // overlapping X), so enabling more than one at once corrupts each other's arena. Warn once at boot.
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            int devTests = (ProgressionConfig.devSpecialTest ? 1 : 0)
                    + (ProgressionConfig.devMechTest ? 1 : 0)
                    + (ProgressionConfig.devClimbTest ? 1 : 0);
            if (devTests > 1) {
                LethalBreed.LOGGER.warn("[LethalBreed] {} dev test arenas enabled at once "
                        + "(devSpecialTest/devMechTest/devClimbTest) — they build overlapping arenas; "
                        + "enable only one per run for clean results.", devTests);
            }
        });

        // Headless climb arena (no-op unless devClimbTest).
        ServerLifecycleEvents.SERVER_STARTED.register(ClimbTest::run);
        // Compute-backend self-test (CPU/GPU parity), in-memory only (no-op unless devComputeTest).
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (ProgressionConfig.devComputeTest) {
                ComputeSelfTest.run(server);
            }
        });

        // Dev/load-test command: /lethalspawn <entity> <count> [delaySeconds].
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                LethalSpawnCommand.register(dispatcher, registryAccess));

        LethalBreed.LOGGER.info("[LethalBreed] dev hooks installed (dev environment only).");
    }
}
