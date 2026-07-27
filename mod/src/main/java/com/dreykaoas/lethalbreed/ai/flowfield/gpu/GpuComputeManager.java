package com.dreykaoas.lethalbreed.ai.flowfield.gpu;

import com.dreykaoas.lethalbreed.LethalBreed;
import com.dreykaoas.lethalbreed.ai.flowfield.FlowField;
import com.dreykaoas.lethalbreed.ai.flowfield.Snapshot;

/**
 * OpenCL (JOCL) compute backend for the flow field — Phase 6. Initialized lazily only when {@code useGpu}
 * is enabled. Detects an AMD GPU (any model), builds the {@code bellman_ford.cl} kernel, and solves a
 * flow-field snapshot on the GPU. Every failure path degrades to the CPU solver, so enabling the GPU can
 * never break the game — at worst it is no win.
 *
 * <p>CPU stays the master path: {@link com.dreykaoas.lethalbreed.ai.flowfield.GpuFlowField} routes a solve
 * to the GPU only when {@code useGpu} is on, a device is available, and the grid is at least
 * {@code gpuMinCells} cells (small fields stay on the CPU, where the GPU round-trip would not pay off).
 *
 * <p>Device pick + context/kernel build live in {@link GpuContext}; per-call buffer marshalling lives
 * in {@link GpuFlowFieldSolver}. This class is the lazy-init facade and shared serialization point.
 */
public final class GpuComputeManager {
    private static final GpuComputeManager INSTANCE = new GpuComputeManager();

    public static GpuComputeManager get() {
        return INSTANCE;
    }

    // volatile: written under the instance monitor (init/solve/logFallbackOnce) but ALSO read without it by
    // the non-blocking command/UI accessors below, so plain fields could publish a stale value across
    // threads (audit #27).
    private volatile boolean initialized = false;
    private volatile boolean available = false;
    private volatile String deviceName = "none";

    /** Consecutive GPU solve failures; reset to 0 on any success. Guarded by this instance's monitor
     *  ({@link #solve} and {@link #logFallbackOnce} are both synchronized). See {@link #FAILURE_LIMIT}. */
    private int consecutiveFailures = 0;

    /**
     * Circuit-breaker threshold: after this many consecutive GPU solve failures the GPU is switched off for
     * the rest of the session ({@code available=false}) and every later solve goes straight to the CPU.
     *
     * <p>Deliberately &gt; 1, not a disable-on-first-failure latch: the codebase intentionally tolerates
     * transient GPU faults (see {@code GpuFlowFieldSolver}'s workgroup-size guard, written expressly to avoid
     * a permanent CPU fallback), so a single {@code CL_OUT_OF_RESOURCES} must not kill the GPU. A run of
     * failures means the device is really gone — and the point is to stop the retry storm (two failing GPU
     * attempts per second per dimension, each re-marshalling a full snapshot), which the old "log once, keep
     * retrying forever" behaviour never did. The context is NOT released here: releasing it from a pool
     * thread while another pool thread is mid-solve is a native use-after-free, and it is a JVM-lived
     * singleton the driver reclaims at exit anyway. Recovery is a server restart (fresh probe).
     */
    private static final int FAILURE_LIMIT = 3;

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

    /** Current known availability WITHOUT taking the monitor or triggering {@link #init}. For command/UI
     *  paths (e.g. {@code /lethalconfig}) that run on the server thread and must neither stall behind an
     *  in-flight solve nor force OpenCL init on a box where the admin set {@code useGpu=false} (audit #9). */
    public boolean isAvailableNonBlocking() {
        return available;
    }

    /** Whether {@link #init} has already run (so a non-blocking reader can tell "no GPU" from "not warmed yet"). */
    public boolean isInitialized() {
        return initialized;
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
     * throws — callers fall back to CPU on any throwable. A successful solve resets the failure breaker.
     */
    public synchronized FlowField solve(Snapshot s) {
        FlowField f = GpuFlowFieldSolver.solve(ctx, s);
        consecutiveFailures = 0; // a good solve clears the breaker so transient blips don't accumulate
        return f;
    }

    /**
     * Record a GPU solve failure (called by {@code GpuFlowField} after it has caught the throwable and fallen
     * back to the CPU for THIS solve). Trips the circuit breaker at {@link #FAILURE_LIMIT} consecutive
     * failures, switching the GPU off for the rest of the session so the retry storm stops. Kept the historic
     * method name; it is no longer a one-shot log.
     */
    public synchronized void logFallbackOnce(Throwable t) {
        if (!available) {
            return; // already tripped; nothing to count or log
        }
        consecutiveFailures++;
        if (consecutiveFailures >= FAILURE_LIMIT) {
            available = false;
            LethalBreed.LOGGER.warn(
                    "[LethalBreed] GPU disabled after {} consecutive solve failures — CPU for the rest of "
                            + "this session (restart to re-probe). Last error: {}",
                    consecutiveFailures, t.toString());
        } else {
            LethalBreed.LOGGER.warn("[LethalBreed] GPU solve failed ({}/{}), retrying then falling back: {}",
                    consecutiveFailures, FAILURE_LIMIT, t.toString());
        }
    }
}
