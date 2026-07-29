package com.dreykaoas.lethalbreed.ai.flowfield;

import com.dreykaoas.lethalbreed.LethalBreed;
import com.dreykaoas.lethalbreed.ai.flowfield.gpu.GpuComputeManager;
import com.dreykaoas.lethalbreed.config.domain.FlowConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

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

            classificationParity(server.overworld());

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
        int d = SIZE;
        Snapshot s = Snapshot.openSquare(SIZE);
        // Carve a wall at x=WALL_X blocking z in [0..d-4], leaving a 3-cell gap near the far edge.
        for (int cz = 0; cz < d - 3; cz++) {
            s.passable[WALL_X * d + cz] = false;
        }
        s.passable[0] = true; // keep the corner (0,0) seed passable
        return s;
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

    /** Classification parity: the chunk-cached snapshot path must produce byte-identical cell types to the
     *  direct ServerLevel path it replaced. Nothing else covers this — the flow-field unit tests all build a
     *  Snapshot by hand and never reach CellClassifier, so a classification regression (a wall that stops
     *  reading as BREAKABLE, a gap that stops reading as BUILDABLE) would silently pass the whole suite and
     *  show up only as zombies that no longer break or bridge.
     *
     *  <p>Also the only place either path's cost is actually measured (task-11: a headless server has no
     *  players, so {@code FlowFieldManager.tick}'s own {@code flowsnap} profiler stage never fires here).
     *  Each path gets one full, uninterrupted sweep of the region before the other starts, so JIT warmup
     *  lands on both runs the same way a standalone run would see it — this is a single measurement, not a
     *  rigorous benchmark, but it is a real one on identical input. */
    private static void classificationParity(ServerLevel level) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int span = 64;
        BlockPos spawn = level.getRespawnData().pos();
        int originX = spawn.getX() - span / 2;
        int originZ = spawn.getZ() - span / 2;
        int focusY = level.getSeaLevel();
        int vtol = FlowConfig.flowVerticalTolerance;

        // Force every chunk the scan touches to be resident BEFORE either pass. A parity check that finds
        // 0 loaded cells proves nothing (task-11 deviation gate) — getChunk(..., FULL, true) synchronously
        // loads/generates each chunk once, then setChunkForced keeps it from unloading mid-test. This is a
        // one-shot dev self-test at server start, not the runtime hot path FlowFieldSnapshotBuilder's
        // "never force a load" comment is protecting.
        int minCx = originX >> 4, maxCx = (originX + span - 1) >> 4;
        int minCz = originZ >> 4, maxCz = (originZ + span - 1) >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                level.getChunk(cx, cz, ChunkStatus.FULL, true);
                level.setChunkForced(cx, cz, true);
            }
        }

        int n = span * span;
        byte[] viaLevel = new byte[n];
        byte[] viaChunk = new byte[n];

        // Pass 1: the pre-refactor oracle (classifyViaLevel), one full sweep, timed alone.
        long t0 = System.nanoTime();
        int idx = 0;
        for (int wx = originX; wx < originX + span; wx++) {
            for (int wz = originZ; wz < originZ + span; wz++, idx++) {
                viaLevel[idx] = CellClassifier.classifyViaLevel(level, m, wx, wz, focusY, vtol);
            }
        }
        long oracleNanos = System.nanoTime() - t0;

        // Pass 2: the new chunk-cached path, same region and order, timed alone.
        long t1 = System.nanoTime();
        idx = 0;
        int checked = 0, mismatches = 0;
        for (int wx = originX; wx < originX + span; wx++) {
            int chunkX = wx >> 4;
            for (int wz = originZ; wz < originZ + span; wz++, idx++) {
                ChunkAccess chunk = level.getChunk(chunkX, wz >> 4, ChunkStatus.FULL, false);
                viaChunk[idx] = (chunk == null)
                        ? CellClassifier.IMPASSABLE
                        : CellClassifier.classify(level, chunk, m, wx, wz, focusY, vtol);
                if (chunk != null) {
                    checked++;
                    if (viaChunk[idx] != viaLevel[idx]) {
                        mismatches++;
                    }
                }
            }
        }
        long chunkNanos = System.nanoTime() - t1;

        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                level.setChunkForced(cx, cz, false);
            }
        }

        log("classify-parity", mismatches == 0 && checked > 0,
                checked + " cells checked, " + mismatches + " mismatched");
        LethalBreed.LOGGER.info(
                "[ComputeTest] classify-parity timing: viaLevel(oracle)={}ms viaChunk(new)={}ms over {} cells ({}x{})",
                String.format("%.2f", oracleNanos / 1_000_000.0),
                String.format("%.2f", chunkNanos / 1_000_000.0), n, span, span);
    }

    private static String backend(int cells, int minCells) {
        return (FlowConfig.useGpu && cells >= minCells && GpuComputeManager.get().isAvailable())
                ? "GPU" : "CPU";
    }

    private static void log(String name, boolean pass, String detail) {
        LethalBreed.LOGGER.info("[ComputeTest] {} : {} ({})", name, pass ? "PASS" : "FAIL", detail);
    }
}
