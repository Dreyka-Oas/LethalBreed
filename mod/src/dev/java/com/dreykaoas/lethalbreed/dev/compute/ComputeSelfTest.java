package com.dreykaoas.lethalbreed.dev.compute;

import com.dreykaoas.lethalbreed.LethalBreed;
import com.dreykaoas.lethalbreed.ai.flowfield.CellClassifier;
import com.dreykaoas.lethalbreed.ai.flowfield.ComputeCalibration;
import com.dreykaoas.lethalbreed.ai.flowfield.CpuFlowField;
import com.dreykaoas.lethalbreed.ai.flowfield.FlowField;
import com.dreykaoas.lethalbreed.ai.flowfield.FlowFieldChecks;
import com.dreykaoas.lethalbreed.ai.flowfield.Snapshot;
import com.dreykaoas.lethalbreed.ai.flowfield.gpu.GpuComputeManager;
import com.dreykaoas.lethalbreed.config.domain.FlowConfig;
import com.dreykaoas.lethalbreed.dev.DevVerdict;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * Dev-only, headless verification of the Compute backend (the GPU/CPU flow-field solvers) plus the world →
 * cell classification front end. Gated by {@code devComputeTest} AND a development environment. The solver
 * checks need no players or world mutation — they build a synthetic {@link Snapshot} in memory and solve it
 * on both backends; the classification check reads (and never writes) a region of the real overworld.
 *
 * <p>Checks, reported through {@link DevVerdict} under the {@code compute} suite:
 * <ul>
 *   <li><b>cpu-sanity</b> — the solver runs: the seed cell is 0 and a far reachable cell is &gt; 0.</li>
 *   <li><b>cpu/gpu-direction, cpu/gpu-optimal</b> — each backend's directions descend and its cost field is
 *       the Bellman fixpoint.</li>
 *   <li><b>gpu-cpu-parity</b> — every cell's cost is identical between the CPU and GPU solvers (the cost
 *       field is the shortest distance, so it is tie-break-independent and must match exactly).</li>
 *   <li><b>dynamic-cpu-pool</b> — solving again after changing {@code flowCpuThreads} rebuilds the pool
 *       without error and yields the same field.</li>
 *   <li><b>classify-coverage</b> — see {@link #classifyCoverage}.</li>
 *   <li><b>calibration</b> — the auto-calibration bench runs end to end and yields a threshold.</li>
 * </ul>
 *
 * <p><b>Why this lives in {@code dev} now.</b> It used to sit in {@code ai.flowfield} in {@code src/main},
 * purely so it could reach the package-private {@link Snapshot}, which meant this whole harness shipped
 * inside the player jar. {@code Snapshot.openSquare} and {@code FlowFieldChecks} are now public (the
 * {@code Snapshot} constructor deliberately is not), so it belongs in the dev source set with every other
 * harness and is compiled only for {@code runClient}/{@code runServer}.
 */
public final class ComputeSelfTest {
    private ComputeSelfTest() {}

    private static final String SUITE = "compute";

    private static final int SIZE = 64;      // 64×64 = 4096 cells (≥ default gpuMinCells, a realistic field)
    private static final int WALL_X = 32;    // a vertical wall with a gap, so routing is non-trivial

    public static void run(MinecraftServer server) {
        try {
            Snapshot s = buildSnapshot();
            FlowField cpu = CpuFlowField.compute(s);

            boolean cpuSane = FlowFieldChecks.cpuSanity(cpu, SIZE, SIZE);
            DevVerdict.check(SUITE, "cpu-sanity", cpuSane, "seed=0 and a far cell is reachable & > 0");

            // Direction field: ties make CPU vs GPU directions legitimately differ, so don't compare them to
            // each other — instead assert each backend's directions are SELF-CONSISTENT (every reachable
            // non-seed cell steps to a strictly cheaper neighbour, i.e. a valid descending gradient).
            DevVerdict.check(SUITE, "cpu-direction", FlowFieldChecks.directionsDescend(cpu, SIZE, SIZE),
                    "every reachable cell steps strictly downhill");
            // Optimality (stronger than descent): the cost field is the Bellman-Ford fixpoint — no passable
            // cell can be relaxed further, so every cost is the true shortest distance, not merely descending.
            DevVerdict.check(SUITE, "cpu-optimal", FlowFieldChecks.costFieldOptimal(s, cpu),
                    "cost field is the Bellman fixpoint (no cell improvable)");

            GpuComputeManager gpu = GpuComputeManager.get();
            if (FlowConfig.useGpu && gpu.isAvailable()) {
                FlowField g = gpu.solve(s);
                int[] diff = FlowFieldChecks.compareCost(s, cpu, g);
                boolean parity = diff[0] == 0;
                DevVerdict.check(SUITE, "gpu-cpu-parity", parity, parity
                        ? "all " + (SIZE * SIZE) + " cells match on " + gpu.deviceName()
                        : diff[0] + " mismatching cells (first @cellIndex=" + diff[1]
                                + " cpu=" + diff[2] + " gpu=" + diff[3] + ")");
                DevVerdict.check(SUITE, "gpu-direction", FlowFieldChecks.directionsDescend(g, SIZE, SIZE),
                        "every reachable cell steps strictly downhill");
                DevVerdict.check(SUITE, "gpu-optimal", FlowFieldChecks.costFieldOptimal(s, g),
                        "cost field is the Bellman fixpoint (no cell improvable)");
            } else {
                // Not a check: a machine with no GPU must not book a failure. Logged under the same prefix so
                // the reason the parity line is missing is still greppable.
                LethalBreed.LOGGER.info("{} {}/gpu-cpu-parity : SKIP (GPU disabled/unavailable)",
                        DevVerdict.PREFIX, SUITE);
            }

            DevVerdict.check(SUITE, "dynamic-cpu-pool", dynamicPoolCheck(s, cpu),
                    "rebuild on flowCpuThreads change, identical field");

            classifyCoverage(server.overworld());

            int small = 16 * 16, large = SIZE * SIZE, min = Math.max(0, FlowConfig.gpuMinCells);
            LethalBreed.LOGGER.info("{} {}/gpuMinCells-routing: min={} | 16x16({}) -> {} | {}x{}({}) -> {}",
                    DevVerdict.PREFIX, SUITE, min, small, backend(small, min), SIZE, SIZE, large,
                    backend(large, min));

            // Exercise the auto-calibration bench end-to-end (it logs its own table); assert it yields a value.
            int cal = ComputeCalibration.calibrate();
            DevVerdict.check(SUITE, "calibration", cal >= 0, "crossover minCells=" + cal);
        } catch (Throwable t) {
            LethalBreed.LOGGER.error("{} {} crashed", DevVerdict.PREFIX, SUITE, t);
            DevVerdict.check(SUITE, "no-crash", false, "threw " + t);
        }
        DevVerdict.summary(SUITE, server);
    }

    /** Flat passable field with one vertical wall (gap at the top) and a single seed in a corner. */
    private static Snapshot buildSnapshot() {
        int d = SIZE;
        Snapshot s = Snapshot.openSquare(SIZE);
        boolean[] passable = s.passable();
        // Carve a wall at x=WALL_X blocking z in [0..d-4], leaving a 3-cell gap near the far edge.
        for (int cz = 0; cz < d - 3; cz++) {
            passable[WALL_X * d + cz] = false;
        }
        passable[0] = true; // keep the corner (0,0) seed passable
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

    /**
     * Classification coverage: sweep a real overworld region through the shipped {@link CellClassifier#classify}
     * and assert that cells were actually classified AND that all four traversal classes still occur.
     *
     * <p><b>What this replaces, and why it was not simply dropped.</b> This check used to be a PARITY check
     * against a second {@code CellClassifier} entry point that was a verbatim copy of the pre-chunk-cache,
     * ServerLevel-only implementation, kept solely as an oracle. That oracle is gone: the manual pass it gated on
     * (parity over 12288 cells with 0 mismatches, plus the climbing / breaking / bridging rigs) came back
     * clean, and a parity check against a copy nobody runs in production only ever protects the copy.
     *
     * <p>The GATE, however, is not redundant. The {@code BreachHarness} behavioural rigs prove BREAKABLE
     * (they breach a sealed dirt box), BUILDABLE (they bridge a trench) and PASSABLE (they walk the plate)
     * end to end, which is stronger evidence than a classification diff. What they do NOT do is assert
     * anything about IMPASSABLE: the bedrock rim exists so nothing wanders off the arena edge, and if it
     * silently started classifying as BREAKABLE the rigs' assertions ("wall broken", "gap bridged") would all
     * still pass. Nothing else in the repo reaches {@link CellClassifier} at all — the flow-field unit tests
     * build a {@link Snapshot} by hand. So a reduced, oracle-free self-consistency gate is kept here.
     *
     * <p>Several focus planes are swept, not one: a single sea-level plane sits underground almost everywhere
     * on a noise overworld and only ever yields PASSABLE/BREAKABLE/IMPASSABLE. Scanning open air well above
     * the terrain is what makes BUILDABLE ("clear feet+head, nothing to stand on") occur.
     */
    private static void classifyCoverage(ServerLevel level) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int span = 64;
        BlockPos spawn = level.getRespawnData().pos();
        int originX = spawn.getX() - span / 2;
        int originZ = spawn.getZ() - span / 2;
        int vtol = FlowConfig.flowVerticalTolerance;
        int[] focusPlanes = { level.getSeaLevel(), level.getSeaLevel() + 24, level.getSeaLevel() + 48 };

        // Force every chunk the scan touches to be resident BEFORE the sweep. A sweep that finds 0 loaded
        // cells proves nothing — getChunk(..., FULL, true) synchronously loads/generates each chunk once,
        // then setChunkForced keeps it from unloading mid-test. This is a one-shot dev self-test at server
        // start, not the runtime hot path FlowFieldSnapshotBuilder's "never force a load" comment protects.
        int minCx = originX >> 4, maxCx = (originX + span - 1) >> 4;
        int minCz = originZ >> 4, maxCz = (originZ + span - 1) >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                level.getChunk(cx, cz, ChunkStatus.FULL, true);
                level.setChunkForced(cx, cz, true);
            }
        }

        // An unbreakable column per focus plane, planted rather than hoped for. Whether the ambient terrain
        // happens to contain one is not this rig's business: swept against a superflat world it found
        // passable=4088, breakable=8, buildable=8192, impassable=0 and failed a COVERAGE gate for want of a
        // fixture. BreachHarness already lays a bedrock rim for the same reason.
        //
        // The column must fill the WHOLE vertical window, not just feet and head. A two-block plug was tried
        // first and produced PASSABLE: classify looks for a standable spot anywhere within vtol of the focus
        // plane, and a short plug is something to stand ON. From one below the window to one above it, there is
        // no standable spot and no gap — which is the definition of IMPASSABLE. Removed again below.
        for (int focusY : focusPlanes) {
            for (int y = focusY - vtol - 1; y <= focusY + vtol + 2; y++) {
                level.setBlock(new BlockPos(originX, y, originZ), Blocks.BEDROCK.defaultBlockState(), 3);
            }
        }

        long nanos = 0L;
        int checked = 0;
        // How many cells of each class occurred, so the log PROVES which branches were exercised instead of
        // implying full coverage with a bare "PASS".
        int[] seen = new int[4];

        for (int focusY : focusPlanes) {
            long t0 = System.nanoTime();
            for (int wx = originX; wx < originX + span; wx++) {
                int chunkX = wx >> 4;
                for (int wz = originZ; wz < originZ + span; wz++) {
                    ChunkAccess chunk = level.getChunk(chunkX, wz >> 4, ChunkStatus.FULL, false);
                    if (chunk == null) {
                        continue; // absent chunk is IMPASSABLE by construction — not evidence about classify
                    }
                    seen[CellClassifier.classify(level, chunk, m, wx, wz, focusY, vtol)]++;
                    checked++;
                }
            }
            nanos += System.nanoTime() - t0;
        }

        for (int focusY : focusPlanes) {
            for (int y = focusY - vtol - 1; y <= focusY + vtol + 2; y++) {
                level.setBlock(new BlockPos(originX, y, originZ), Blocks.AIR.defaultBlockState(), 3);
            }
        }
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                level.setChunkForced(cx, cz, false);
            }
        }

        // Every branch must actually have been hit. A sweep that never produced a BUILDABLE cell says nothing
        // about the bridge path — an unexercised class is a FAILED gate, exactly like a 0-cell sweep.
        boolean allBranches = seen[CellClassifier.PASSABLE] > 0 && seen[CellClassifier.BREAKABLE] > 0
                && seen[CellClassifier.BUILDABLE] > 0 && seen[CellClassifier.IMPASSABLE] > 0;
        DevVerdict.check(SUITE, "classify-coverage", checked > 0 && allBranches,
                checked + " cells classified in "
                        + String.format(java.util.Locale.ROOT, "%.2f", nanos / 1_000_000.0) + "ms over "
                        + focusPlanes.length + " planes (" + span + "x" + span + "), classes"
                        + " passable=" + seen[CellClassifier.PASSABLE]
                        + " breakable=" + seen[CellClassifier.BREAKABLE]
                        + " buildable=" + seen[CellClassifier.BUILDABLE]
                        + " impassable=" + seen[CellClassifier.IMPASSABLE]);
    }

    private static String backend(int cells, int minCells) {
        return (FlowConfig.useGpu && cells >= minCells && GpuComputeManager.get().isAvailable())
                ? "GPU" : "CPU";
    }
}
