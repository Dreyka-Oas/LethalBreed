package com.dreykaoas.lethalbreed.dev;

import com.dreykaoas.lethalbreed.dev.arena.BreachHarness;
import com.dreykaoas.lethalbreed.dev.contam.ClearGuardHarness;
import com.dreykaoas.lethalbreed.dev.arena.ClimbTest;
import com.dreykaoas.lethalbreed.dev.command.DevSpawnScheduler;
import com.dreykaoas.lethalbreed.dev.contam.LeakProbeHarness;
import com.dreykaoas.lethalbreed.dev.command.LethalDevCommand;
import com.dreykaoas.lethalbreed.dev.command.LethalPhaseCommand;
import com.dreykaoas.lethalbreed.dev.command.LethalSpawnCommand;
import com.dreykaoas.lethalbreed.dev.command.LethalSpecialCommand;
import com.dreykaoas.lethalbreed.dev.arena.PlacedBlockHarness;
import com.dreykaoas.lethalbreed.dev.contam.ContaminationIndicator;
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
import com.dreykaoas.lethalbreed.config.ConfigBounds;
import com.dreykaoas.lethalbreed.config.schema.ConfigSchema;
import com.dreykaoas.lethalbreed.dev.config.DevBounds;
import com.dreykaoas.lethalbreed.dev.config.DevTestConfig;
import com.dreykaoas.lethalbreed.probe.DevProbe;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/**
 * Single entry point for every development-only feature: the dev config holder ({@code DevTestConfig} and
 * its {@link DevBounds}), all of {@code main}'s instrumentation (the stage profiler, the perf recap, the dev
 * counters and the debug traces, installed here behind the {@link DevProbe} seam), plus the headless test
 * harnesses and every developer command ({@code /lethalspawn}, {@code /lethaldev}, {@code /lethalphase},
 * {@code /lethalspecial}). Lives in the {@code dev} source set, so it is
 * compiled and packaged ONLY for {@code runClient}/{@code runServer} — a shipped/remapped jar never contains
 * this class or anything under {@code com.dreykaoas.lethalbreed.dev}.
 *
 * <p>{@code main} never imports this class directly; both entry points are loaded by reflection from
 * {@code LethalBreedMod}'s private {@code devHook(String)} helper — called twice, as
 * {@code devHook("registerConfig")} and {@code devHook("install")} — and only when
 * {@code FabricLoader.isDevelopmentEnvironment()} is true. On a production jar the reflective lookup simply
 * fails ({@link ClassNotFoundException}) and is swallowed, so no dev wiring ever runs — and, since
 * {@link #registerConfig} is where the dev options join the schema, a player's config file and config GUI
 * have no dev options and no "Dev / Debug" tab.
 *
 * <p>There are two entry points because they must straddle the config load: {@link #registerConfig} before
 * it, {@link #install} after it. See each for why.
 */
public final class DevBootstrap {
    private DevBootstrap() {}

    /**
     * Phase 1 of 2: put the dev config holder into the schema. Called reflectively by the main mod
     * initializer BEFORE {@code BootstrapInit.run()} reads {@code lethalbreed.json}.
     *
     * <p>The ordering is the whole point. The loader is field-driven: a key it cannot match to a schema field
     * is reported as unknown and dropped on the next write. Register after the load and a developer's own dev
     * options are warned about and then deleted from their own config file on first launch.
     *
     * <p>Kept apart from {@link #install} because that one ends with {@link DevTestSelector#apply()}, which
     * FORCES exactly one suite flag on — a decision the JSON load would overwrite if it ran afterwards. So
     * the two halves straddle the load: schema first, selection last.
     */
    public static void registerConfig() {
        ConfigSchema.registerHolder(DevTestConfig.class);
        ConfigBounds.registerGroup(DevBounds::register);
    }

    /**
     * Phase 2 of 2: wire every dev hook. Called reflectively by the main mod initializer (dev env only),
     * AFTER the config load. The signature is a stable, argument-free contract so the reflective call site
     * in {@code main} needs no knowledge of the dev classes.
     */
    public static void install() {
        // FIRST of all: hand main its instrumentation. Everything below (and every harness) reads through
        // the sink, so it must exist before any dev flag is applied or any tick listener is registered.
        // The profiler is built first and handed to BOTH collaborators — the sink feeds it, the recap
        // drains it — so neither has to be patched up after construction.
        StageProfiler profiler = new StageProfiler();
        PerfRecap recap = new PerfRecap(GameState.REGISTRY, GameState.DIMENSIONS, profiler);
        // Each trace channel is opt-in, defaulting off (DevTestConfig.debugClimb/debugPacks/debugContam are
        // all false for shipping-shaped runs). A harness that needs its own channel for its own run/stage
        // flips it at runtime with DevProbe.setTracing — see ClimbTest and PackSetup.
        int traceMask = (DevTestConfig.debugClimb ? 1 << DevProbe.CLIMB : 0)
                | (DevTestConfig.debugPacks ? 1 << DevProbe.PACKS : 0)
                | (DevTestConfig.debugContam ? 1 << DevProbe.CONTAM : 0);
        DevProbe.install(new DevSink(profiler, recap), traceMask);

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
        // Live action-bar/name-tag indicator for every tracked contamination victim. Gated on the
        // DevProbe.CONTAM channel (DevTestConfig.debugContam), like every other trace channel.
        ServerTickEvents.END_SERVER_TICK.register(ContaminationIndicator::onTick);
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

        // Dev commands: /lethalspawn (load-test spawn), /lethaldev (exercise slow effects on demand),
        // /lethalphase (force the difficulty phase) and /lethalspecial (spawn a forced special type).
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LethalSpawnCommand.register(dispatcher, registryAccess);
            LethalDevCommand.register(dispatcher);
            LethalPhaseCommand.register(dispatcher);
            LethalSpecialCommand.register(dispatcher);
        });

        LethalBreed.LOGGER.info("[LethalBreed] dev hooks installed (dev environment only).");
    }
}
