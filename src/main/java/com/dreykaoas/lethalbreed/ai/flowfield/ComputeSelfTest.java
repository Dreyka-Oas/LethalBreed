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

            boolean cpuSane = FlowFieldChecks.cpuSanity(cpu, SIZE, SIZE);
            log("cpu-sanity", cpuSane, "seed=0 and a far cell is reachable & > 0");

            // Direction field: ties make CPU vs GPU directions legitimately differ, so don't compare them to
            // each other — instead assert each backend's directions are SELF-CONSISTENT (every reachable
            // non-seed cell steps to a strictly cheaper neighbour, i.e. a valid descending gradient).
            log("cpu-direction", FlowFieldChecks.directionsDescend(cpu, SIZE, SIZE), "every reachable cell steps strictly downhill");
            // Optimality (stronger than descent): the cost field is the Bellman-Ford fixpoint — no passable
            // cell can be relaxed further, so every cost is the true shortest distance, not merely descending.
            log("cpu-optimal", FlowFieldChecks.costFieldOptimal(s, cpu), "cost field is the Bellman fixpoint (no cell improvable)");

            GpuComputeManager gpu = GpuComputeManager.get();
            if (FlowConfig.useGpu && gpu.isAvailable()) {
                FlowField g = gpu.solve(s);
                int[] diff = FlowFieldChecks.compareCost(s, cpu, g);
                boolean parity = diff[0] == 0;
                log("gpu-cpu-parity", parity, parity
                        ? "all " + (SIZE * SIZE) + " cells match on " + gpu.deviceName()
                        : diff[0] + " mismatching cells (first @cellIndex=" + diff[1]
                                + " cpu=" + diff[2] + " gpu=" + diff[3] + ")");
                log("gpu-direction", FlowFieldChecks.directionsDescend(g, SIZE, SIZE), "every reachable cell steps strictly downhill");
                log("gpu-optimal", FlowFieldChecks.costFieldOptimal(s, g), "cost field is the Bellman fixpoint (no cell improvable)");
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

    /** Solve again with a different thread count to force a pool rebuild; field must be unchanged. */
    private static boolean dynamicPoolCheck(Snapshot s, FlowField reference) {
        int saved = FlowConfig.flowCpuThreads;
        try {
            FlowConfig.flowCpuThreads = saved == 1 ? 2 : 1; // guaranteed different -> rebuild
            FlowField again = CpuFlowField.compute(s);
            return FlowFieldChecks.compareCost(s, reference, again)[0] == 0;
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
