package com.dreykaoas.lethalbreed.gpu;

import com.dreykaoas.lethalbreed.ai.flowfield.CpuFlowField;
import com.dreykaoas.lethalbreed.ai.flowfield.FlowField;
import com.dreykaoas.lethalbreed.config.LethalBreedConfig;

/**
 * Dispatcher chosen by {@link FlowFieldManager}'s worker task. Uses the GPU when enabled and
 * available, otherwise the CPU solver. Any GPU error degrades to CPU — the GPU is never load-bearing.
 */
public final class GpuFlowField {
    private GpuFlowField() {}

    public static FlowField compute(CpuFlowField.Snapshot s) {
        if (LethalBreedConfig.useGpu) {
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
