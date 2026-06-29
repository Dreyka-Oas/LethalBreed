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
        // Size gate: below the threshold the GPU upload/round-trip overhead beats its throughput, so small
        // fields stay on the CPU. Both solvers yield the identical cost field — this trades latency only.
        // The threshold is the auto-calibrated crossover when gpuAutoCalibrate is on (and a calibration has
        // run), else the manual gpuMinCells.
        int cells = s.width() * s.depth();
        int minCells;
        int calibrated = ComputeCalibration.minCells();
        if (FlowConfig.gpuAutoCalibrate && calibrated >= 0) {
            minCells = calibrated;
        } else {
            minCells = Math.max(0, FlowConfig.gpuMinCells);
        }
        if (FlowConfig.useGpu && cells >= minCells) {
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
