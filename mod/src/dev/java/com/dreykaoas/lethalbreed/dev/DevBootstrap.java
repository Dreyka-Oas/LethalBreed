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
        // FIRST, before anything reads a dev flag: let LB_DEV_TEST / -Dlethalbreed.devTest force exactly one
        // suite on and every other one off. Every gate below (and every SERVER_STARTED listener registered
        // here) therefore sees the selected configuration, not whatever the config file was left holding.
        DevTestSelector.apply();

        // Headless verification arenas, driven per server tick. Each harness self-gates on its own
        // ProgressionConfig flag (devSpecialTest / devMechTest) AND the dev-env check, so registering the
        // tick listener here is harmless when the flags are off.
        ServerTickEvents.END_SERVER_TICK.register(SpecialTestHarness::onTick);
        ServerTickEvents.END_SERVER_TICK.register(MechanicsTestHarness::onTick);
        // Foundation self-test: proves the synthetic player is present and the flow field / pathing follow.
        ServerTickEvents.END_SERVER_TICK.register(PresenceHarness::onTick);
        // Contamination rigs. "clear" is standalone; the three "plague" rigs share one run and are serialised
        // by the start offsets in ContamRig (they mutate process-global plague config), with the disable rig
        // last because it owns the suite summary.
        ServerTickEvents.END_SERVER_TICK.register(ClearGuardHarness::onTick);
        ServerTickEvents.END_SERVER_TICK.register(PlagueDamageHarness::onTick);
        ServerTickEvents.END_SERVER_TICK.register(LeakProbeHarness::onTick);
        ServerTickEvents.END_SERVER_TICK.register(PlagueDisableHarness::onTick);
        // Load-test spawn queue, drained each server tick (no-op unless /lethalspawn queued work).
        ServerTickEvents.END_SERVER_TICK.register(DevSpawnScheduler::tick);

        // Dev arenas build blocks at overlapping coordinates (special + mechanics share Y=101 with overlapping
        // X), so enabling more than one at once corrupts each other's arena. This used to be a warn-and-hope
        // message at SERVER_STARTED — it detected the corruption and then produced corrupted results anyway.
        // DevTestSelector.apply() above now FORCES the invariant instead (exactly one flag on). All that is
        // left is to report a config file that still has several on with no selection in force, since in that
        // case the operator's own values are (deliberately) still being honoured.
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (DevTestSelector.selected() != null) {
                return; // selection in force — exactly one flag is on by construction
            }
            int devTests = (ProgressionConfig.devSpecialTest ? 1 : 0)
                    + (ProgressionConfig.devMechTest ? 1 : 0)
                    + (ProgressionConfig.devClimbTest ? 1 : 0)
                    + (ProgressionConfig.devPresenceTest ? 1 : 0);
            if (devTests > 1) {
                LethalBreed.LOGGER.warn("[LethalBreed] {} dev test arenas enabled at once in the config file — "
                        + "they build overlapping arenas. Set {}=<suite> (one of: {}) to force exactly one.",
                        devTests, DevTestSelector.ENV_VAR, "special, mech, climb, compute, plague, statue, "
                                + "clear, placed, shade, breach, presence");
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

        // Dev commands: /lethalspawn (load-test spawn) + /lethaldev (exercise slow effects on demand).
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LethalSpawnCommand.register(dispatcher, registryAccess);
            LethalDevCommand.register(dispatcher);
        });

        LethalBreed.LOGGER.info("[LethalBreed] dev hooks installed (dev environment only).");
    }
}
