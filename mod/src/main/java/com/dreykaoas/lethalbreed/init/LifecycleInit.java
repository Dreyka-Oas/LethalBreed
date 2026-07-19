package com.dreykaoas.lethalbreed.init;

import com.dreykaoas.lethalbreed.config.domain.FlowConfig;

import com.dreykaoas.lethalbreed.ai.flowfield.ComputeCalibration;
import com.dreykaoas.lethalbreed.ai.flowfield.gpu.GpuComputeManager;
import com.dreykaoas.lethalbreed.dimension.DimensionManager;
import com.dreykaoas.lethalbreed.entity.ZombieRegistry;
import com.dreykaoas.lethalbreed.phase.PhaseManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/**
 * Registers server start/stop lifecycle hooks. Dev-only start hooks (headless climb / compute self-test)
 * live in the {@code dev} source set and are wired by {@code DevBootstrap}, not here.
 */
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
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            registry.clear();
            dimensions.clear();
        });
    }
}
