package com.dreykaoas.lethalbreed.ai.flowfield;


import com.dreykaoas.lethalbreed.ai.flowfield.cpu.CpuFlowField;
import com.dreykaoas.lethalbreed.config.domain.engine.FlowConfig;

import com.dreykaoas.lethalbreed.ai.flowfield.gpu.GpuComputeManager;

/**
 * Dispatcher chosen by {@link FlowFieldManager}'s worker task. Uses the GPU when enabled and
 * available, otherwise the CPU solver. Any GPU error degrades to CPU: the GPU is never load-bearing.
 *
 * <p>No size gate: a device that is present takes every solve, whatever the grid. There used to be one,
 * with a per-machine crossover the server could measure at boot. Both were dropped: the smallest field the
 * game actually builds is 49×49 (a lone player plus the default 24-block margin), already past the
 * crossover a nine-boot benchmark measured on this hardware, so the gate only ever fired for grids the
 * snapshot builder cannot produce, and the boot measurement disagreed with itself between runs.
 */
public final class GpuFlowField {
    private GpuFlowField() {}

    public static FlowField compute(Snapshot s) {
        if (FlowConfig.useGpu) {
            GpuComputeManager gpu = GpuComputeManager.get();
            if (gpu.isAvailable()) {
                try {
                    return gpu.solve(s);
                } catch (Throwable t) {
                    gpu.logFallbackOnce(t);
                }
            }
        }
        return CpuFlowField.compute(s);
    }
}
