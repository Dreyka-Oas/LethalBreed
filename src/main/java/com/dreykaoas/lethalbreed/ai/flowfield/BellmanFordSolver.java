package com.dreykaoas.lethalbreed.ai.flowfield;

import com.dreykaoas.lethalbreed.config.domain.FlowConfig;


import java.util.Arrays;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

/**
 * WORKER THREAD: solve a {@link Snapshot} with PARALLEL Bellman-Ford relaxation — the multi-core CPU
 * backup used whenever the GPU is absent/disabled. Each iteration every passable cell pulls the
 * cheapest cost from its 8 neighbours (across the {@link #pool()} threads); repeat until a
 * fixpoint (nothing improved) or the safety cap. This is the same algorithm as the GPU
 * {@code relax_step} kernel and converges to the identical shortest-cost field as a sequential
 * Dijkstra (non-negative weights). No Minecraft access.
 *
 * <p>Race-free without locks: each parallel task writes only its OWN cell ({@code cost[i]}, disjoint
 * across tasks). Neighbour reads may be a tick stale within an iteration (Gauss-Seidel) — benign for
 * Bellman-Ford, at worst one extra iteration. The {@code submit(...).join()} barrier between
 * iterations publishes all writes to the next pass.
 */
final class BellmanFordSolver {
    private BellmanFordSolver() {}

    private static final int[] NDX = {1, -1, 0, 0, 1, 1, -1, -1};
    private static final int[] NDZ = {0, 0, 1, -1, 1, -1, 1, -1};

    /** Daemon worker factory for the CPU solve pool. */
    private static final ForkJoinPool.ForkJoinWorkerThreadFactory SOLVE_FACTORY = pool -> {
        ForkJoinWorkerThread t = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
        t.setName("LethalBreed-CpuSolve");
        t.setDaemon(true);
        return t;
    };

    private static volatile ForkJoinPool solvePool;     // lazily built, rebuilt on a flowCpuThreads change
    private static volatile int solvePoolThreads = -1;   // thread count the current pool was sized for

    private static int resolveSolveThreads() {
        int cfg = FlowConfig.flowCpuThreads;
        int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
        // cfg>0: honour the request but cap RELATIVE to the host cores (cores*4) — a ForkJoinPool can't exceed
        // its MAX_CAP and massive oversubscription only adds contention. cfg<=0: auto = cores-2. This is a
        // runtime-relative anti-oversubscription bound, not a static config range (those live in ConfigBounds).
        return cfg > 0 ? Math.min(cfg, cores * 4) : Math.max(1, cores - 2);
    }

    /** CPU solve pool, rebuilt when {@link FlowConfig#flowCpuThreads} changes so a GUI/command edit takes
     *  effect without a JVM restart. Rebuilds are rare (only on a config change) and the superseded pool is
     *  shut down. Synchronized because solves run on the 2-thread FlowField daemon pool — two solves could
     *  otherwise race to rebuild. */
    private static synchronized ForkJoinPool pool() {
        int want = resolveSolveThreads();
        if (solvePool == null || want != solvePoolThreads) {
            // Replace the pool but DO NOT shut the old one down: a concurrent solve on another dimension may
            // still hold a reference to it and be about to submit(), and shutdown() would make that submit()
            // throw RejectedExecutionException. Instead the pool is built with a short keep-alive, so once a
            // superseded pool goes idle its daemon workers terminate within a few seconds and the pool is
            // GC'd — bounded, no leak, no race. Rebuilds happen only when flowCpuThreads actually changes.
            solvePool = new ForkJoinPool(want, SOLVE_FACTORY, null, false,
                    want, want, 1, null, 5L, TimeUnit.SECONDS);
            solvePoolThreads = want;
        }
        return solvePool;
    }

    static FlowField compute(Snapshot s) {
        final int width = s.width, depth = s.depth, n = width * depth;
        final boolean[] passable = s.passable;
        final int[] extra = s.extraCost;
        final short[] cost = new short[n];
        Arrays.fill(cost, FlowField.IMPASSABLE);
        final byte[] dirX = new byte[n];
        final byte[] dirZ = new byte[n];
        // Step costs are config-driven and shared with the GPU kernel so both solvers yield the same field.
        final int orth = Math.max(1, FlowConfig.flowOrthoCost);
        final int diagCost = Math.max(orth, FlowConfig.flowDiagonalCost);
        for (int seed : s.seedCells) {
            cost[seed] = 0;
        }

        int maxIter = width + depth + 2; // safety cap; converges in ~graph-diameter passes, breaks early
        for (int iter = 0; iter < maxIter; iter++) {
            AtomicBoolean changed = new AtomicBoolean(false);
            pool().submit(() -> IntStream.range(0, n).parallel().forEach(i -> {
                if (!passable[i]) {
                    return;
                }
                int cx = i / depth;
                int cz = i % depth;
                int cur = cost[i];
                int best = cur;
                for (int k = 0; k < 8; k++) {
                    int nx = cx + NDX[k];
                    int nz = cz + NDZ[k];
                    if (nx < 0 || nx >= width || nz < 0 || nz >= depth) {
                        continue;
                    }
                    int nc = cost[nx * depth + nz];
                    if (nc >= FlowField.IMPASSABLE) {
                        continue;
                    }
                    boolean diag = NDX[k] != 0 && NDZ[k] != 0;
                    if (diag && (!passable[cx * depth + nz] || !passable[nx * depth + cz])) {
                        continue; // no corner cutting
                    }
                    int cand = nc + (diag ? diagCost : orth) + extra[i]; // entering i costs extra[i]
                    if (cand < best) {
                        best = cand;
                    }
                }
                if (best < cur) {
                    cost[i] = (short) Math.min(best, FlowField.IMPASSABLE - 1);
                    changed.set(true);
                }
            })).join();
            if (!changed.get()) {
                break; // fixpoint
            }
        }

        // Direction extraction: each cell points to its cheapest reachable neighbour. Parallel — every cell
        // writes only its own dirX[i]/dirZ[i], so no races.
        pool().submit(() -> IntStream.range(0, n).parallel().forEach(i -> {
            if (cost[i] >= FlowField.IMPASSABLE || cost[i] == 0) {
                return;
            }
            int cx = i / depth;
            int cz = i % depth;
            int bestCost = cost[i];
            int bdx = 0, bdz = 0;
            for (int k = 0; k < 8; k++) {
                int nx = cx + NDX[k];
                int nz = cz + NDZ[k];
                if (nx < 0 || nx >= width || nz < 0 || nz >= depth) {
                    continue;
                }
                int ni = nx * depth + nz;
                if (cost[ni] >= FlowField.IMPASSABLE) {
                    continue;
                }
                boolean diag = NDX[k] != 0 && NDZ[k] != 0;
                if (diag && (!passable[cx * depth + nz] || !passable[nx * depth + cz])) {
                    continue;
                }
                if (cost[ni] < bestCost) {
                    bestCost = cost[ni];
                    bdx = NDX[k];
                    bdz = NDZ[k];
                }
            }
            dirX[i] = (byte) bdx;
            dirZ[i] = (byte) bdz;
        })).join();

        return new FlowField(s.originX, s.originZ, width, depth, s.focusY, cost, dirX, dirZ, s.flags);
    }
}
