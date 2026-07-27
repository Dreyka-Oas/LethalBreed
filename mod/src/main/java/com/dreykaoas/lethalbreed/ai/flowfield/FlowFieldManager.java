package com.dreykaoas.lethalbreed.ai.flowfield;

import com.dreykaoas.lethalbreed.LethalBreed;
import com.dreykaoas.lethalbreed.config.domain.FlowConfig;

import com.dreykaoas.lethalbreed.util.Players;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Owns the active flow field for one dimension. The world snapshot is captured on the server thread;
 * the Dijkstra solve runs on a shared daemon pool and the result is swapped in atomically. Zombie
 * ticks read {@link #active()} lock-free.
 */
public final class FlowFieldManager {
    /** Shared low-priority daemon pool for all dimensions' flow-field solves. */
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "LethalBreed-FlowField");
        t.setDaemon(true);
        t.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1));
        return t;
    });

    private final AtomicReference<FlowField> active = new AtomicReference<>();
    private final AtomicBoolean computing = new AtomicBoolean(false);
    private long lastComputeTick = Long.MIN_VALUE;
    private double lastFocusX = Double.NaN;
    private double lastFocusZ = Double.NaN;

    /** Consecutive solve failures, and the tick of the last logged failure — so a persistent failure is
     *  reported (with its running count) but not once per cycle. Written on the worker thread and the server
     *  thread; {@code volatile} is enough since neither reads-then-writes based on the other's value. */
    private volatile int consecutiveFailures = 0;
    private volatile long lastFailureLogTick = Long.MIN_VALUE;

    public FlowField active() {
        return active.get();
    }

    /** SERVER THREAD: throttled. Snapshots the world here, solves off-thread. */
    public void tick(ServerLevel level, long serverTick) {
        int interval = Math.max(1, FlowConfig.flowRecomputeInterval);
        if (serverTick - lastComputeTick < interval) {
            return;
        }
        if (computing.get()) {
            return; // previous solve still running; skip this cycle
        }

        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (Players.isTargetable(p)) {
                players.add(p);
            }
        }
        if (players.isEmpty()) {
            active.set(null);
            lastComputeTick = serverTick;
            return;
        }

        // Optional move-gate: when the players' focus centre has barely shifted since the last solve and a
        // field already exists, reuse it instead of re-solving (saves the solve while players stand still).
        double focusX = 0.0, focusZ = 0.0;
        for (ServerPlayer p : players) {
            focusX += p.getX();
            focusZ += p.getZ();
        }
        focusX /= players.size();
        focusZ /= players.size();
        double moveGate = FlowConfig.flowResampleOnMoveDist;
        if (moveGate > 0.0 && active.get() != null && !Double.isNaN(lastFocusX)) {
            double mdx = focusX - lastFocusX;
            double mdz = focusZ - lastFocusZ;
            if (mdx * mdx + mdz * mdz < moveGate * moveGate) {
                lastComputeTick = serverTick; // consume this cycle; recheck after the next interval
                return;
            }
        }

        lastComputeTick = serverTick;
        lastFocusX = focusX;
        lastFocusZ = focusZ;
        Snapshot snapshot = CpuFlowField.snapshot(level, players); // main thread read
        computing.set(true);
        POOL.submit(() -> {
            try {
                active.set(GpuFlowField.compute(snapshot)); // GPU if enabled+available, else CPU
                consecutiveFailures = 0;
            } catch (Throwable t) {
                // GpuFlowField already catches GPU errors and degrades to CPU (and the GPU circuit breaker
                // logs those), so reaching here means the CPU solver ITSELF threw — previously swallowed
                // silently into a discarded Future. Log it (rate-limited) rather than re-solving a throwing
                // snapshot every interval with no trace. Do NOT null `active`: the last good field is a far
                // better fallback than none, and there is no other "known" field to swap to.
                onSolveFailure(serverTick, t);
            } finally {
                computing.set(false);
            }
        });
    }

    /** Record a solve failure and log it at most once per ~10 s window, with the running consecutive count so
     *  a persistent failure is visible without one line per cycle. (POOL.submit can't itself throw here: the
     *  queue is unbounded and the pool is never shut down, so there is no reject path to guard — audit #19.) */
    private void onSolveFailure(long serverTick, Throwable t) {
        int n = ++consecutiveFailures;
        long since = serverTick - lastFailureLogTick;
        if (lastFailureLogTick == Long.MIN_VALUE || since >= 200L) { // ~10 s at 20 tps
            lastFailureLogTick = serverTick;
            LethalBreed.LOGGER.error(
                    "[LethalBreed] flow-field CPU solve failed ({} in a row), keeping last field: {}",
                    n, t.toString());
        }
    }
}
