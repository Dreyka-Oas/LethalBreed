package oas.dreyka.lethalbreed.ai.flowfield;


import oas.dreyka.lethalbreed.ai.flowfield.cpu.CpuFlowField;
import oas.dreyka.lethalbreed.config.domain.engine.FlowConfig;

import oas.dreyka.lethalbreed.ai.flowfield.gpu.GpuComputeManager;

/**
 * Dispatcher chosen by {@link FlowFieldManager}'s worker task. Uses the GPU when enabled and
 * available, otherwise the CPU solver. Any GPU error degrades to CPU: the GPU is never load-bearing.
 *
 * <p>No size gate: a device that is present takes every solve, whatever the grid. There used to be one, with
 * a per-machine crossover the server measured at boot, and the boot measurement disagreed with itself between
 * runs. The gate went with it. The solve is not on the server thread either way (FlowFieldManager runs it on
 * two dedicated low-priority threads, which carry the GPU pass just as well), so below the crossover the card
 * costs field latency rather than TPS, and leaves the CPU solve pool idle instead (SolvePool, cores-2 workers
 * by default). On a 9060 XT the bench wanders between runs and puts that crossover somewhere between 64x64
 * and 128x128, in every run above the 49x49 floor a lone player produces: the trade is deliberate, not a
 * measurement that says the card is always faster.
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
