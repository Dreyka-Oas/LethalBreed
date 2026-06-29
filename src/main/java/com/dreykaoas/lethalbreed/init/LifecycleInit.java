package com.dreykaoas.lethalbreed.init;

import com.dreykaoas.lethalbreed.config.domain.FlowConfig;
import com.dreykaoas.lethalbreed.config.domain.ProgressionConfig;

import com.dreykaoas.lethalbreed.ai.flowfield.gpu.GpuComputeManager;
import com.dreykaoas.lethalbreed.dev.ClimbTest;
import com.dreykaoas.lethalbreed.dimension.DimensionManager;
import com.dreykaoas.lethalbreed.entity.ZombieRegistry;
import com.dreykaoas.lethalbreed.phase.PhaseManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/** Registers server start/stop lifecycle hooks in their original order. */
public final class LifecycleInit {
    private LifecycleInit() {}

    public static void register(ZombieRegistry registry, DimensionManager dimensions) {
        // Warm the GPU compute backend at boot (when enabled) so its detection line — GPU name or CPU
        // fallback — is logged once at startup instead of lazily on the first flow-field solve.
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (FlowConfig.useGpu) {
                GpuComputeManager.get().isAvailable();
            }
            PhaseManager.get().reset(); // start each session at phase 1
        });

        // Dev-only headless climb test arena (no-op unless ProgressionConfig.devClimbTest is on).
        ServerLifecycleEvents.SERVER_STARTED.register(ClimbTest::run);

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            registry.clear();
            dimensions.clear();
        });
    }
}
