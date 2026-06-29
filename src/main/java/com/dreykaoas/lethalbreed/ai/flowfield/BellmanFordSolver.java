package com.dreykaoas.lethalbreed.ai.flowfield;

import com.dreykaoas.lethalbreed.config.domain.FlowConfig;


import java.util.Arrays;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

/**
 * WORKER THREAD: solve a {@link Snapshot} with PARALLEL Bellman-Ford relaxation — the multi-core CPU
 * backup used whenever the GPU is absent/disabled. Each iteration every passable cell pulls the
 * cheapest cost from its 8 neighbours (across {@link #SOLVE_THREADS} threads); repeat until a
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

    private static final int ORTH = 10;
    private static final int DIAG = 14;
    private static final int[] NDX = {1, -1, 0, 0, 1, 1, -1, -1};
    private static final int[] NDZ = {0, 0, 1, -1, 1, -1, 1, -1};

    /** Threads used to parallelize ONE flow-field solve when there's no GPU (the multi-core CPU backup). */
    private static final int SOLVE_THREADS = resolveSolveThreads();
    private static final ForkJoinPool SOLVE_POOL = new ForkJoinPool(SOLVE_THREADS,
            pool -> {
                ForkJoinWorkerThread t = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
                t.setName("LethalBreed-CpuSolve");
                t.setDaemon(true);
                return t;
            }, null, false);

    private static int resolveSolveThreads() {
        int cfg = FlowConfig.flowCpuThreads;
        return cfg > 0 ? cfg : Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
    }

    static FlowField compute(Snapshot s) {
        final int width = s.width, depth = s.depth, n = width * depth;
        final boolean[] passable = s.passable;
        final int[] extra = s.extraCost;
        final short[] cost = new short[n];
        Arrays.fill(cost, FlowField.IMPASSABLE);
        final byte[] dirX = new byte[n];
        final byte[] dirZ = new byte[n];
        for (int seed : s.seedCells) {
            cost[seed] = 0;
        }

        int maxIter = width + depth + 2; // safety cap; converges in ~graph-diameter passes, breaks early
        for (int iter = 0; iter < maxIter; iter++) {
            AtomicBoolean changed = new AtomicBoolean(false);
            SOLVE_POOL.submit(() -> IntStream.range(0, n).parallel().forEach(i -> {
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
                    int cand = nc + (diag ? DIAG : ORTH) + extra[i]; // entering i costs extra[i]
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
        SOLVE_POOL.submit(() -> IntStream.range(0, n).parallel().forEach(i -> {
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
