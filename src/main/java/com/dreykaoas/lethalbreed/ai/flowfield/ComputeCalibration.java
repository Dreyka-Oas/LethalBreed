package com.dreykaoas.lethalbreed.ai.flowfield;

import com.dreykaoas.lethalbreed.LethalBreed;
import com.dreykaoas.lethalbreed.ai.flowfield.gpu.GpuComputeManager;

/**
 * One-off micro-benchmark of the CPU vs GPU flow-field solver on THIS machine, used to auto-pick the
 * {@code gpuMinCells} crossover when {@code gpuAutoCalibrate} is on. Solves a synthetic open field at a
 * range of sizes on both backends and finds the smallest grid where the GPU is faster than the CPU — below
 * it the GPU's upload/round-trip overhead loses. Lives in the {@code flowfield} package to build the
 * package-private {@link Snapshot} directly. Pure in-memory, no world/players.
 */
public final class ComputeCalibration {
    private ComputeCalibration() {}

    /** Grid sides benchmarked (cells = side²). Ascending: the first GPU win is the crossover. Spans up to
     *  192, the {@code flowMaxGrid} cap, so the largest real field is covered. */
    private static final int[] SIDES = {16, 24, 32, 48, 64, 96, 128, 160, 192};
    private static final int RUNS = 5; // min-of-RUNS timing to cut scheduler noise

    /** Calibrated threshold in cells; -1 until {@link #calibrate()} runs. Read by {@link GpuFlowField}. */
    private static volatile int minCells = -1;

    public static int minCells() {
        return minCells;
    }

    /** Run the benchmark and store the crossover. Returns the chosen threshold (cells). */
    public static synchronized int calibrate() {
        GpuComputeManager gpu = GpuComputeManager.get();
        if (!gpu.isAvailable()) {
            minCells = 0; // no GPU — threshold irrelevant (CPU always runs anyway)
            LethalBreed.LOGGER.info("[LethalBreed] GPU calibration skipped (no GPU) — minCells=0");
            return minCells;
        }
        int crossover = -1;
        StringBuilder table = new StringBuilder();
        for (int side : SIDES) {
            Snapshot s = openField(side);
            CpuFlowField.compute(s);   // warm up JIT / pool
            gpu.solve(s);              // warm up GPU buffers
            long cpu = bestOf(() -> CpuFlowField.compute(s));
            long g = bestOf(() -> gpu.solve(s));
            table.append(String.format(" %d²:cpu=%.2fms/gpu=%.2fms", side, cpu / 1e6, g / 1e6));
            if (crossover < 0 && g < cpu) {
                crossover = side * side;
            }
        }
        // No GPU win in range → set just above the largest tested grid so the GPU effectively stays off.
        int largest = SIDES[SIDES.length - 1] * SIDES[SIDES.length - 1];
        minCells = (crossover < 0) ? largest + 1 : crossover;
        LethalBreed.LOGGER.info("[LethalBreed] GPU calibration: minCells={} |{}", minCells, table);
        return minCells;
    }

    /** Flat, fully passable square field with a single corner seed — a clean timing workload. */
    private static Snapshot openField(int side) {
        int n = side * side;
        boolean[] passable = new boolean[n];
        java.util.Arrays.fill(passable, true);
        int[] extraCost = new int[n];
        byte[] flags = new byte[n];
        return new Snapshot(0, 0, side, side, 64, passable, extraCost, flags, new int[]{0});
    }

    private static long bestOf(Runnable solve) {
        long best = Long.MAX_VALUE;
        for (int i = 0; i < RUNS; i++) {
            long t0 = System.nanoTime();
            solve.run();
            best = Math.min(best, System.nanoTime() - t0);
        }
        return best;
    }
}
