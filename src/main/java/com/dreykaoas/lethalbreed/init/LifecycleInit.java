package com.dreykaoas.lethalbreed.init;

import com.dreykaoas.lethalbreed.config.domain.FlowConfig;
import com.dreykaoas.lethalbreed.config.domain.ProgressionConfig;

import com.dreykaoas.lethalbreed.ai.flowfield.ComputeCalibration;
import com.dreykaoas.lethalbreed.ai.flowfield.ComputeSelfTest;
import com.dreykaoas.lethalbreed.ai.flowfield.gpu.GpuComputeManager;
import com.dreykaoas.lethalbreed.dev.ClimbTest;
import com.dreykaoas.lethalbreed.dimension.DimensionManager;
import com.dreykaoas.lethalbreed.entity.ZombieRegistry;
import com.dreykaoas.lethalbreed.LethalBreed;
import com.dreykaoas.lethalbreed.phase.PhaseManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;

/** Registers server start/stop lifecycle hooks in their original order. */
public final class LifecycleInit {
    private LifecycleInit() {}

    public static void register(ZombieRegistry registry, DimensionManager dimensions) {
        // Warm the GPU compute backend at boot (when enabled) so its detection line — GPU name or CPU
        // fallback — is logged once at startup instead of lazily on the first flow-field solve.
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (FlowConfig.useGpu) {
                GpuComputeManager.get().isAvailable();
                // Auto-calibrate the CPU↔GPU crossover on this machine when asked (one-off boot cost).
                if (FlowConfig.gpuAutoCalibrate) {
                    ComputeCalibration.calibrate();
                }
            }
            PhaseManager.get().load(server); // restore the persisted phase (survives close/reopen)

            // Dev tests build block arenas; the tick-driven ones (special + mechanics) share Y=101 with
            // overlapping X, so enabling more than one at once corrupts each other's arena. Warn once at boot
            // (dev env only — the harnesses are no-ops elsewhere, so the warning would be misleading there).
            if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
                int devTests = (ProgressionConfig.devSpecialTest ? 1 : 0)
                        + (ProgressionConfig.devMechTest ? 1 : 0)
                        + (ProgressionConfig.devClimbTest ? 1 : 0);
                if (devTests > 1) {
                    LethalBreed.LOGGER.warn("[LethalBreed] {} dev test arenas enabled at once "
                            + "(devSpecialTest/devMechTest/devClimbTest) — they build overlapping arenas; "
                            + "enable only one per run for clean results.", devTests);
                }
            }
        });

        // Dev-only headless climb test arena (no-op unless ProgressionConfig.devClimbTest is on).
        ServerLifecycleEvents.SERVER_STARTED.register(ClimbTest::run);

        // Dev-only Compute-backend self-test (CPU/GPU parity). In-memory only — no world mutation — but still
        // dev-env gated for consistency with the other dev harnesses.
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (ProgressionConfig.devComputeTest && FabricLoader.getInstance().isDevelopmentEnvironment()) {
                ComputeSelfTest.run(server);
            }
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            registry.clear();
            dimensions.clear();
        });
    }
}
