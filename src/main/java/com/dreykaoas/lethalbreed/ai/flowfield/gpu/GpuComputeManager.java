package com.dreykaoas.lethalbreed.ai.flowfield.gpu;

import com.dreykaoas.lethalbreed.LethalBreed;
import com.dreykaoas.lethalbreed.ai.flowfield.FlowField;
import com.dreykaoas.lethalbreed.ai.flowfield.Snapshot;

/**
 * OpenCL (JOCL) compute backend for the flow field — Phase 6, <b>benchmark-gated</b>. Initialized
 * lazily only when {@code useGpu} is enabled. Detects an AMD GPU (any model), builds the
 * {@code bellman_ford.cl} kernel, and solves a flow-field snapshot on the GPU. Every failure path
 * degrades to the CPU solver, so enabling the GPU can never break the game — at worst it is no win.
 *
 * <p>CPU stays the master path; the GPU is only used when a benchmark proves it helps.
 *
 * <p>Device pick + context/kernel build live in {@link GpuContext}; per-call buffer marshalling lives
 * in {@link GpuFlowFieldSolver}. This class is the lazy-init facade and shared serialization point.
 */
public final class GpuComputeManager {
    private static final GpuComputeManager INSTANCE = new GpuComputeManager();

    public static GpuComputeManager get() {
        return INSTANCE;
    }

    private boolean initialized = false;
    private boolean available = false;
    private String deviceName = "none";
    private boolean fallbackLogged = false;

    private GpuContext ctx;

    private GpuComputeManager() {}

    public synchronized boolean isAvailable() {
        if (!initialized) {
            init();
        }
        return available;
    }

    public String deviceName() {
        return deviceName;
    }

    private void init() {
        initialized = true;
        try {
            this.ctx = new GpuContext();
            this.deviceName = ctx.deviceName;
            this.available = true;
            LethalBreed.LOGGER.info("[LethalBreed] GPU: {} — OpenCL OK", ctx.deviceName);
        } catch (Throwable t) {
            available = false;
            LethalBreed.LOGGER.warn("[LethalBreed] GPU: unavailable — CPU fallback activated ({})", t.toString());
        }
    }

    /**
     * Solve a snapshot on the GPU. Serialized (single shared queue). Returns a {@link FlowField} or
     * throws — callers fall back to CPU on any throwable.
     */
    public synchronized FlowField solve(Snapshot s) {
        return GpuFlowFieldSolver.solve(ctx, s);
    }

    public void logFallbackOnce(Throwable t) {
        if (!fallbackLogged) {
            fallbackLogged = true;
            LethalBreed.LOGGER.warn("[LethalBreed] GPU solve failed once, using CPU from now on: {}", t.toString());
        }
    }
}
