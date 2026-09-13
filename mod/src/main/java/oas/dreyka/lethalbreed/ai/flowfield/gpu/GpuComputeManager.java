package oas.dreyka.lethalbreed.ai.flowfield.gpu;

import oas.dreyka.lethalbreed.LethalBreed;
import oas.dreyka.lethalbreed.ai.flowfield.FlowField;
import oas.dreyka.lethalbreed.ai.flowfield.Snapshot;

/**
 * OpenCL (JOCL) compute backend for the flow field (Phase 6). Initialized lazily only when {@code useGpu}
 * is enabled. Detects an AMD GPU (any model), builds the {@code bellman_ford.cl} kernel, and solves a
 * flow-field snapshot on the GPU. Every failure path degrades to the CPU solver, so enabling the GPU can
 * never break the game: at worst it is no win.
 *
 * <p>CPU stays the master path: {@link oas.dreyka.lethalbreed.ai.flowfield.GpuFlowField} routes a solve
 * to the GPU when {@code useGpu} is on and a device is available, whatever the grid size, and to the CPU
 * otherwise.
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
    // threads.
    private volatile boolean initialized = false;
    private volatile boolean available = false;
    private volatile String deviceName = "none";

    /** Consecutive GPU solve failures; reset to 0 on any success. Guarded by this instance's monitor
     *  ({@link #solve} and {@link #logFallbackOnce} are both synchronized). See {@link #FAILURE_LIMIT}. */
    private int consecutiveFailures = 0;

    /** Guarded by the same monitor as {@link #solve}; volatile so {@link #solveCount} can read it without
     *  queueing behind an in-flight solve. */
    private volatile long solves = 0L;

    /**
     * Circuit-breaker threshold: after this many consecutive GPU solve failures the GPU is switched off for
     * the rest of the session ({@code available=false}) and every later solve goes straight to the CPU.
     *
     * <p>Deliberately &gt; 1, not a disable-on-first-failure latch: the codebase intentionally tolerates
     * transient GPU faults (see {@code GpuFlowFieldSolver}'s workgroup-size guard, written expressly to avoid
     * a permanent CPU fallback), so a single {@code CL_OUT_OF_RESOURCES} must not kill the GPU. A run of
     * failures means the device is really gone, and the point is to stop the retry storm (two failing GPU
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
     *  in-flight solve nor force OpenCL init on a box where the admin set {@code useGpu=false}. */
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
            LethalBreed.LOGGER.info("[LethalBreed] GPU: {} (OpenCL OK)", ctx.deviceName);
        } catch (Throwable t) {
            available = false;
            LethalBreed.LOGGER.warn("[LethalBreed] GPU: unavailable, CPU fallback activated ({})", t.toString());
        }
    }

    /**
     * Solve a snapshot on the GPU. Returns a {@link FlowField} or throws. Callers fall back to CPU on any
     * throwable. A successful solve resets the failure breaker.
     *
     * <p><b>The lock is required, not cautious.</b> Kernel arguments are state on the {@code cl_kernel}
     * object, and the solver sets ten of them before it enqueues. OpenCL excludes exactly that call from
     * its thread-safety guarantee: {@code clSetKernelArg} on one kernel object from two host threads at
     * once is undefined, and even a lock around the single call would not help, since the second thread
     * could overwrite the arguments between the first thread's last set and its enqueue. The whole
     * sequence has to be atomic. Letting the two flow-field threads run concurrently means one
     * {@code cl_kernel} and one queue each, not a narrower lock.
     */
    public synchronized FlowField solve(Snapshot s) {
        FlowField f = GpuFlowFieldSolver.solve(ctx, s);
        consecutiveFailures = 0; // a good solve clears the breaker so transient blips don't accumulate
        solves++;
        return f;
    }

    /** Successful GPU solves this session. The dev compute suite reads it to observe which backend
     *  {@code GpuFlowField} actually picked, rather than re-deriving the dispatcher's own condition and
     *  asserting it against itself. */
    public long solveCount() {
        return solves;
    }

    /**
     * Record a GPU solve failure (called by {@code GpuFlowField} after it has caught the throwable and fallen
     * back to the CPU for THIS solve). Trips the circuit breaker at {@link #FAILURE_LIMIT} consecutive
     * failures, switching the GPU off for the rest of the session so the retry storm stops. The {@code Once}
     * in the name is about the log line, not about the counting: every failure is counted.
     */
    public synchronized void logFallbackOnce(Throwable t) {
        if (!available) {
            return; // already tripped; nothing to count or log
        }
        consecutiveFailures++;
        if (consecutiveFailures >= FAILURE_LIMIT) {
            available = false;
            LethalBreed.LOGGER.warn(
                    "[LethalBreed] GPU disabled after {} consecutive solve failures, CPU for the rest of "
                            + "this session (restart to re-probe). Last error: {}",
                    consecutiveFailures, t.toString());
        } else {
            LethalBreed.LOGGER.warn("[LethalBreed] GPU solve failed ({}/{}), retrying then falling back: {}",
                    consecutiveFailures, FAILURE_LIMIT, t.toString());
        }
    }
}
