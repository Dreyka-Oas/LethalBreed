package com.dreykaoas.lethalbreed.ai.flowfield;

import com.dreykaoas.lethalbreed.LethalBreed;
import com.dreykaoas.lethalbreed.ai.flowfield.gpu.GpuComputeManager;
import com.dreykaoas.lethalbreed.config.domain.FlowConfig;
import net.minecraft.server.MinecraftServer;

/**
 * Dev-only, headless verification of the Compute backend (the GPU/CPU flow-field solvers). Gated by
 * {@code devComputeTest} AND a development environment. Needs no players or world mutation — it builds a
 * synthetic {@link Snapshot} in memory and solves it on both backends.
 *
 * <p>Checks, logging PASS/FAIL per check:
 * <ul>
 *   <li><b>CPU sanity</b> — the solver runs: the seed cell is 0 and a far reachable cell is &gt; 0.</li>
 *   <li><b>GPU/CPU parity</b> — every cell's cost is identical between the CPU and GPU solvers (the cost
 *       field is the shortest distance, so it is tie-break-independent and must match exactly).</li>
 *   <li><b>Dynamic CPU pool</b> — solving again after changing {@code flowCpuThreads} rebuilds the pool
 *       without error and yields the same field.</li>
 *   <li><b>gpuMinCells routing</b> — reports the backend {@link GpuFlowField} would pick for a tiny vs a
 *       large grid, confirming the size gate is wired.</li>
 * </ul>
 * This lives in the {@code flowfield} package so it can build the package-private {@link Snapshot} directly.
 */
public final class ComputeSelfTest {
    private ComputeSelfTest() {}

    private static final int SIZE = 64;      // 64×64 = 4096 cells (≥ default gpuMinCells, a realistic field)
    private static final int WALL_X = 32;    // a vertical wall with a gap, so routing is non-trivial

    public static void run(MinecraftServer server) {
        try {
            Snapshot s = buildSnapshot();
            FlowField cpu = CpuFlowField.compute(s);

            boolean cpuSane = cpuSanity(cpu);
            log("cpu-sanity", cpuSane, "seed=0 and a far cell is reachable & > 0");

            // Direction field: ties make CPU vs GPU directions legitimately differ, so don't compare them to
            // each other — instead assert each backend's directions are SELF-CONSISTENT (every reachable
            // non-seed cell steps to a strictly cheaper neighbour, i.e. a valid descending gradient).
            log("cpu-direction", directionsDescend(cpu), "every reachable cell steps strictly downhill");
            // Optimality (stronger than descent): the cost field is the Bellman-Ford fixpoint — no passable
            // cell can be relaxed further, so every cost is the true shortest distance, not merely descending.
            log("cpu-optimal", costFieldOptimal(s, cpu), "cost field is the Bellman fixpoint (no cell improvable)");

            GpuComputeManager gpu = GpuComputeManager.get();
            if (FlowConfig.useGpu && gpu.isAvailable()) {
                FlowField g = gpu.solve(s);
                int[] diff = compareCost(s, cpu, g);
                boolean parity = diff[0] == 0;
                log("gpu-cpu-parity", parity, parity
                        ? "all " + (SIZE * SIZE) + " cells match on " + gpu.deviceName()
                        : diff[0] + " mismatching cells (first @cellIndex=" + diff[1]
                                + " cpu=" + diff[2] + " gpu=" + diff[3] + ")");
                log("gpu-direction", directionsDescend(g), "every reachable cell steps strictly downhill");
                log("gpu-optimal", costFieldOptimal(s, g), "cost field is the Bellman fixpoint (no cell improvable)");
            } else {
                LethalBreed.LOGGER.info("[ComputeTest] gpu-cpu-parity : SKIP (GPU disabled/unavailable)");
            }

            boolean poolOk = dynamicPoolCheck(s, cpu);
            log("dynamic-cpu-pool", poolOk, "rebuild on flowCpuThreads change, identical field");

            int small = 16 * 16, large = SIZE * SIZE, min = Math.max(0, FlowConfig.gpuMinCells);
            LethalBreed.LOGGER.info("[ComputeTest] gpuMinCells-routing: min={} | 16x16({}) -> {} | {}x{}({}) -> {}",
                    min, small, backend(small, min), SIZE, SIZE, large, backend(large, min));

            // Exercise the auto-calibration bench end-to-end (it logs its own table); assert it yields a value.
            int cal = ComputeCalibration.calibrate();
            log("calibration", cal >= 0, "crossover minCells=" + cal);

            LethalBreed.LOGGER.info("[ComputeTest] DONE");
        } catch (Throwable t) {
            LethalBreed.LOGGER.error("[ComputeTest] crashed", t);
        }
    }

    /** Flat passable field with one vertical wall (gap at the top) and a single seed in a corner. */
    private static Snapshot buildSnapshot() {
        int w = SIZE, d = SIZE, n = w * d;
        boolean[] passable = new boolean[n];
        int[] extraCost = new int[n];
        byte[] flags = new byte[n];
        for (int cx = 0; cx < w; cx++) {
            for (int cz = 0; cz < d; cz++) {
                int i = cx * d + cz;
                // Wall at x=WALL_X blocks z in [0..d-4]; leave a 3-cell gap near the far edge.
                passable[i] = !(cx == WALL_X && cz < d - 3);
            }
        }
        int seed = 0; // corner (0,0)
        passable[seed] = true;
        int[] seedCells = {seed};
        return new Snapshot(0, 0, w, d, 64, passable, extraCost, flags, seedCells);
    }

    /** Every reachable non-seed cell must sample a direction that steps to a STRICTLY cheaper neighbour —
     *  a valid descending gradient toward a goal. Tie-break-independent, so it holds for both backends. */
    private static boolean directionsDescend(FlowField f) {
        int[] dir = new int[2];
        for (int cx = 0; cx < SIZE; cx++) {
            for (int cz = 0; cz < SIZE; cz++) {
                int here = f.costAt(cx, cz);
                if (here <= 0 || here >= FlowField.IMPASSABLE) {
                    continue; // seed (0) or unreachable: no direction expected
                }
                if (!f.sampleInto(cx, cz, dir)) {
                    return false; // a reachable non-seed cell must yield a direction
                }
                if (f.costAt(cx + dir[0], cz + dir[1]) >= here) {
                    return false; // the step must reduce cost (descend toward the goal)
                }
            }
        }
        return true;
    }

    /** The cost field must satisfy the Bellman-Ford optimality condition: every passable non-seed reachable
     *  cell equals the cheapest {@code neighbourCost + step + enterCost} over its valid (non-corner-cut)
     *  neighbours. If any cell could still be relaxed, the field is suboptimal. Uses the same step costs and
     *  corner rule as the solvers, read from {@link FlowConfig}. */
    private static boolean costFieldOptimal(Snapshot s, FlowField f) {
        int w = s.width(), d = s.depth();
        boolean[] pass = s.passable();
        int[] extra = s.extraCost();
        int orth = Math.max(1, FlowConfig.flowOrthoCost);
        int diag = Math.max(orth, FlowConfig.flowDiagonalCost);
        int[] ndx = {1, -1, 0, 0, 1, 1, -1, -1};
        int[] ndz = {0, 0, 1, -1, 1, -1, 1, -1};
        for (int cx = 0; cx < w; cx++) {
            for (int cz = 0; cz < d; cz++) {
                if (!pass[cx * d + cz]) {
                    continue;
                }
                int here = f.costAt(cx, cz);
                if (here == 0 || here >= FlowField.IMPASSABLE) {
                    continue; // seed or unreachable — no relaxation constraint
                }
                int best = Integer.MAX_VALUE;
                for (int k = 0; k < 8; k++) {
                    int nx = cx + ndx[k], nz = cz + ndz[k];
                    if (nx < 0 || nx >= w || nz < 0 || nz >= d) {
                        continue;
                    }
                    boolean dg = ndx[k] != 0 && ndz[k] != 0;
                    if (dg && (!pass[cx * d + nz] || !pass[nx * d + cz])) {
                        continue; // no corner cutting
                    }
                    int nc = f.costAt(nx, nz);
                    if (nc >= FlowField.IMPASSABLE) {
                        continue;
                    }
                    best = Math.min(best, nc + (dg ? diag : orth) + extra[cx * d + cz]);
                }
                if (best != here) {
                    return false; // could be relaxed (suboptimal) or inconsistent
                }
            }
        }
        return true;
    }

    private static boolean cpuSanity(FlowField f) {
        // Seed corner must be 0; the opposite corner (across the wall gap) must be reachable and positive.
        return f.costAt(0, 0) == 0
                && f.costAt(SIZE - 1, SIZE - 1) > 0
                && f.costAt(SIZE - 1, SIZE - 1) < FlowField.IMPASSABLE;
    }

    /** Returns {mismatchCount, firstIndex, cpuCost, gpuCost}. */
    private static int[] compareCost(Snapshot s, FlowField a, FlowField b) {
        int mism = 0, firstIdx = -1, cpuV = 0, gpuV = 0;
        for (int cx = 0; cx < s.width(); cx++) {
            for (int cz = 0; cz < s.depth(); cz++) {
                int ca = a.costAt(cx, cz);
                int cb = b.costAt(cx, cz);
                if (ca != cb) {
                    if (firstIdx < 0) {
                        firstIdx = cx * s.depth() + cz;
                        cpuV = ca;
                        gpuV = cb;
                    }
                    mism++;
                }
            }
        }
        return new int[]{mism, firstIdx, cpuV, gpuV};
    }

    /** Solve again with a different thread count to force a pool rebuild; field must be unchanged. */
    private static boolean dynamicPoolCheck(Snapshot s, FlowField reference) {
        int saved = FlowConfig.flowCpuThreads;
        try {
            FlowConfig.flowCpuThreads = saved == 1 ? 2 : 1; // guaranteed different -> rebuild
            FlowField again = CpuFlowField.compute(s);
            return compareCost(s, reference, again)[0] == 0;
        } finally {
            FlowConfig.flowCpuThreads = saved;
        }
    }

    private static String backend(int cells, int minCells) {
        return (FlowConfig.useGpu && cells >= minCells && GpuComputeManager.get().isAvailable())
                ? "GPU" : "CPU";
    }

    private static void log(String name, boolean pass, String detail) {
        LethalBreed.LOGGER.info("[ComputeTest] {} : {} ({})", name, pass ? "PASS" : "FAIL", detail);
    }
}
