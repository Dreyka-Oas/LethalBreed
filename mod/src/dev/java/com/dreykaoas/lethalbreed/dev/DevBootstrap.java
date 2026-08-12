package com.dreykaoas.lethalbreed.dev;

import com.dreykaoas.lethalbreed.dev.arena.BreachHarness;
import com.dreykaoas.lethalbreed.dev.contam.ClearGuardHarness;
import com.dreykaoas.lethalbreed.dev.arena.ClimbTest;
import com.dreykaoas.lethalbreed.dev.command.DevSpawnScheduler;
import com.dreykaoas.lethalbreed.dev.contam.LeakProbeHarness;
import com.dreykaoas.lethalbreed.dev.command.LethalDevCommand;
import com.dreykaoas.lethalbreed.dev.command.LethalSpawnCommand;
import com.dreykaoas.lethalbreed.dev.arena.PlacedBlockHarness;
import com.dreykaoas.lethalbreed.dev.contam.PlagueDamageHarness;
import com.dreykaoas.lethalbreed.dev.contam.PlagueDisableHarness;
import com.dreykaoas.lethalbreed.dev.arena.PresenceHarness;
import com.dreykaoas.lethalbreed.dev.arena.pack.PackHarness;
import com.dreykaoas.lethalbreed.dev.arena.shade.ShadeHarness;
import com.dreykaoas.lethalbreed.dev.arena.statue.StatueHarness;

import com.dreykaoas.lethalbreed.GameState;
import com.dreykaoas.lethalbreed.LethalBreed;
import com.dreykaoas.lethalbreed.dev.compute.ComputeSelfTest;
import com.dreykaoas.lethalbreed.dev.probe.DevSink;
import com.dreykaoas.lethalbreed.dev.probe.PerfRecap;
import com.dreykaoas.lethalbreed.dev.probe.StageProfiler;
import com.dreykaoas.lethalbreed.config.domain.engine.DevTestConfig;
import com.dreykaoas.lethalbreed.probe.DevProbe;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/**
 * Single entry point for every development-only feature: all of {@code main}'s instrumentation (the stage
 * profiler, the perf recap, the dev counters and the debug traces, installed here behind the
 * {@link DevProbe} seam), plus the headless test harnesses and the {@code /lethalspawn} load-test command.
 * Lives in the {@code dev} source set, so it is compiled and packaged ONLY for
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
        // FIRST of all: hand main its instrumentation. Everything below (and every harness) reads through
        // the sink, so it must exist before any dev flag is applied or any tick listener is registered.
        // The profiler is built first and handed to BOTH collaborators — the sink feeds it, the recap
        // drains it — so neither has to be patched up after construction.
        StageProfiler profiler = new StageProfiler();
        PerfRecap recap = new PerfRecap(GameState.REGISTRY, GameState.DIMENSIONS, profiler);
        DevProbe.install(new DevSink(profiler, recap),
                (1 << DevProbe.CLIMB) | (1 << DevProbe.PACKS) | (1 << DevProbe.CONTAM));

        // FIRST, before anything reads a dev flag: let LB_DEV_TEST / -Dlethalbreed.devTest force exactly one
        // suite on and every other one off. Every gate below (and every SERVER_STARTED listener registered
        // here) therefore sees the selected configuration, not whatever the config file was left holding.
        DevTestSelector.apply();

        // Headless verification arenas, driven per server tick. Each harness self-gates on its own
        // ProgressionConfig flag (devSpecialTest / devMechTest) AND the dev-env check, so registering the
        // tick listener here is harmless when the flags are off.
        ServerTickEvents.END_SERVER_TICK.register(SpecialTestHarness::onTick);
        ServerTickEvents.END_SERVER_TICK.register(MechanicsTestHarness.INSTANCE::onTick);
        // Foundation self-test: proves the synthetic player is present and the flow field / pathing follow.
        ServerTickEvents.END_SERVER_TICK.register(PresenceHarness.INSTANCE::onTick);
        ServerTickEvents.END_SERVER_TICK.register(PackHarness.INSTANCE::onTick);
        // Contamination rigs. "clear" is standalone; the three "plague" rigs share one run and are serialised
        // by the start offsets in ContamRig (they mutate process-global plague config), with the disable rig
        // last because it owns the suite summary.
        ServerTickEvents.END_SERVER_TICK.register(ClearGuardHarness.INSTANCE::onTick);
        ServerTickEvents.END_SERVER_TICK.register(PlagueDamageHarness.INSTANCE::onTick);
        ServerTickEvents.END_SERVER_TICK.register(LeakProbeHarness.INSTANCE::onTick);
        ServerTickEvents.END_SERVER_TICK.register(PlagueDisableHarness.INSTANCE::onTick);
        // Statue / placed-block / shade / breach rigs, and the climb arena (which is a tick harness now, not a
        // one-shot SERVER_STARTED scenario — see ClimbTest). Each self-gates on its own ProgressionConfig flag.
        ServerTickEvents.END_SERVER_TICK.register(StatueHarness::onTick);
        // The statue rig's whole verdict is "what did the entity carry when it was deserialised", and that
        // value is overwritten by the mod's own next mood tick. ENTITY_LOAD is the only place it can be read.
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD
                .register(StatueHarness::onEntityLoad);
        ServerTickEvents.END_SERVER_TICK.register(PlacedBlockHarness::onTick);
        ServerTickEvents.END_SERVER_TICK.register(ShadeHarness::onTick);
        ServerTickEvents.END_SERVER_TICK.register(BreachHarness::onTick);
        ServerTickEvents.END_SERVER_TICK.register(ClimbTest::onTick);
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
            java.util.List<String> on = DevTestSelector.enabled();
            if (on.size() > 1) {
                LethalBreed.LOGGER.warn("[LethalBreed] {} dev test arenas enabled at once in the config file "
                        + "({}) — they build overlapping arenas and force-load overlapping chunks. "
                        + "Set {}=<suite> (one of: {}) to force exactly one.",
                        on.size(), String.join(", ", on), DevTestSelector.ENV_VAR,
                        String.join(", ", DevTestSelector.names()));
            }
        });

        // Compute-backend self-test (CPU/GPU parity + cell-classification coverage); no-op unless
        // devComputeTest. Reports through DevVerdict under the "compute" suite like every other harness.
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (DevTestConfig.devComputeTest) {
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
