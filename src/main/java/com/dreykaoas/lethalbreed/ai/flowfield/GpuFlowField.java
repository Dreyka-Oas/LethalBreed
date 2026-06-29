package com.dreykaoas.lethalbreed.ai.flowfield;

import com.dreykaoas.lethalbreed.config.domain.FlowConfig;

import com.dreykaoas.lethalbreed.ai.flowfield.gpu.GpuComputeManager;

/**
 * Dispatcher chosen by {@link FlowFieldManager}'s worker task. Uses the GPU when enabled and
 * available, otherwise the CPU solver. Any GPU error degrades to CPU — the GPU is never load-bearing.
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
