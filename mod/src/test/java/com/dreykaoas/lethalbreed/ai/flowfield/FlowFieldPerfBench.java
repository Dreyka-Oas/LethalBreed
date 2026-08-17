package com.dreykaoas.lethalbreed.ai.flowfield;


import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.dreykaoas.lethalbreed.config.domain.engine.FlowConfig;

/**
 * Headless API perf bench for the LethalBreed flow-field AI core. NO Minecraft, NO game server.
 *
 * <p>The flow field is the shared pathfinding cost surface every zombie samples. Per server tick the
 * cost is:
 * <ul>
 *   <li><b>SOLVE</b> — one Bellman-Ford solve of the grid ({@link CpuFlowField#compute}). Shared across
 *       ALL zombies in a dimension, recomputed when the target region moves. Scales with grid area, NOT
 *       with zombie count.</li>
 *   <li><b>SAMPLE</b> — each zombie reads its own cell + the cell ahead in O(1) ({@link FlowField#sampleInto}).
 *       This is the ONLY per-zombie flow cost. Scales linearly with zombie count.</li>
 * </ul>
 *
 * <p>Total per-tick flow cost ≈ solve + N × sample. The bench measures both so the "is it optimised for
 * 5000 zombies?" question can be answered from real numbers on THIS machine. Run:
 * {@code ./gradlew test -Plb.bench=true --tests "com.dreykaoas.lethalbreed.ai.flowfield.FlowFieldPerfBench"}
 * then read the printed tables (gradle: add {@code --info} or check the test stdout).
 *
 * <p><b>Why it is gated off by default.</b> This is a measurement rig, not an assertion test: its single
 * {@code assertTrue} only checks that the warm-up solve produced a field at all, and everything else it
 * produces is printed tables that no automated run reads. Left on it cost 2.5 s of the suite's 3.2 s — 78 %
 * of every build's test time — to print numbers into a log nobody opens. Its sibling
 * {@code RoutingQualityMeasureTest} was deleted outright, because it carried no {@code @Test} and JUnit
 * therefore never ran it at all; this one does run, and its numbers are worth keeping reachable.
 *
 * <p><b>Why a system property and not {@code @Disabled}.</b> {@code @Disabled} would not leave the bench
 * runnable: Gradle's {@code --tests} selects which tests to run, it does not lift a disable condition, and
 * lifting one needs {@code junit.jupiter.conditions.deactivate} inside the FORKED test JVM — which a bare
 * {@code -D} on the Gradle command line never reaches. {@code build.gradle.kts} forwards {@code -Plb.bench}
 * into that JVM instead, so the command line above genuinely works.
 */
@EnabledIfSystemProperty(named = "lb.bench", matches = "true",
        disabledReason = "measurement bench — run with -Plb.bench=true (see the class javadoc)")
class FlowFieldPerfBench {

    private static final int WARMUP = 30;   // JIT + ForkJoinPool warm-up runs (discarded)
    private static final int RUNS = 50;     // timed runs; min-of taken to cut scheduler noise

    /** Grid sides to solve. Spans real in-game sizes up to the flowMaxGrid=192 cap, plus 256 as headroom. */
    private static final int[] SIDES = {32, 48, 64, 96, 128, 160, 192, 256};

    /** Zombie population points requested. */
    private static final int[] ZOMBIES = {100, 500, 1000, 2500, 5000, 10000};

    private static long bestNanos(Runnable r) {
        for (int i = 0; i < WARMUP; i++) r.run();
        long best = Long.MAX_VALUE;
        for (int i = 0; i < RUNS; i++) {
            long t0 = System.nanoTime();
            r.run();
            best = Math.min(best, System.nanoTime() - t0);
        }
        return best;
    }

    @Test
    void benchmark() {
        int cores = Runtime.getRuntime().availableProcessors();
        System.out.println();
        System.out.println("================ LethalBreed FlowField API Perf Bench ================");
        System.out.println("cores=" + cores + "  flowCpuThreads=" + FlowConfig.flowCpuThreads
                + " (0=auto=cores-2)  ortho=" + FlowConfig.flowOrthoCost
                + " diag=" + FlowConfig.flowDiagonalCost);
        System.out.println("warmup=" + WARMUP + " timedRuns=" + RUNS + " (min-of)");
        System.out.println();

        // ---- Phase 1: SOLVE cost per grid size (shared across all zombies) ----
        System.out.println("--- SOLVE (one Bellman-Ford solve, shared by ALL zombies in a dimension) ---");
        System.out.printf("%-8s %-10s %-12s %-14s%n", "side", "cells", "solve(ms)", "ns/cell");
        long solve192 = -1;
        for (int side : SIDES) {
            Snapshot s = Snapshot.openSquare(side);
            FlowField warm = CpuFlowField.compute(s);
            Assertions.assertTrue(warm.costAt(side - 1, side - 1) > 0, "field solved");
            long ns = bestNanos(() -> CpuFlowField.compute(s));
            long cells = (long) side * side;
            System.out.printf("%-8d %-10d %-12.3f %-14.2f%n",
                    side, cells, ns / 1e6, (double) ns / cells);
            if (side == 192) solve192 = ns;
        }
        System.out.println();

        // ---- Phase 2: SAMPLE cost per zombie count (the per-zombie flow work) ----
        // Solve one representative 192² field (the flowMaxGrid cap) then time N random samples,
        // exactly what N zombies do each tick against the shared field.
        int side = 192;
        FlowField field = CpuFlowField.compute(Snapshot.openSquare(side));
        int[] out = new int[2];
        // Deterministic pseudo-random cell coords (no Math.random — reproducible).
        System.out.println("--- SAMPLE (per-zombie O(1) flow read against a solved 192² field) ---");
        System.out.printf("%-9s %-14s %-14s %-16s%n", "zombies", "sample(ms)", "ns/zombie", "solve+sample(ms)");
        long solveCost = (solve192 > 0) ? solve192 : bestNanos(() -> CpuFlowField.compute(Snapshot.openSquare(192)));
        long blackhole = 0;
        for (int nz : ZOMBIES) {
            final int count = nz;
            long ns = bestNanos(() -> {
                long acc = 0;
                int x = 1, z = 1;
                for (int i = 0; i < count; i++) {
                    // walk a scrambled path over the grid so reads hit varied cache lines
                    x = (x * 1103515245 + 12345) & 0x7fffffff;
                    z = (z * 1103515245 + 76543) & 0x7fffffff;
                    int wx = x % side, wz = z % side;
                    field.sampleInto(wx, wz, out);
                    acc += out[0] + out[1] + field.costAt(wx, wz);
                }
                if (acc == Long.MIN_VALUE) System.out.print(""); // prevent DCE
            });
            blackhole += ns;
            double perTickMs = (solveCost + ns) / 1e6;
            System.out.printf("%-9d %-14.4f %-14.2f %-16.4f%n",
                    nz, ns / 1e6, (double) ns / nz, perTickMs);
        }
        if (blackhole == Long.MIN_VALUE) System.out.print("");
        System.out.println();

        // ---- Verdict ----
        double solveMs = solveCost / 1e6;
        System.out.println("--- VERDICT: 5000 zombies, 192² shared field ---");
        System.out.printf("solve (shared, once/tick when region moves) : %.3f ms%n", solveMs);
        // recompute a clean 5000 sample number
        long s5000 = bestNanos(() -> {
            long acc = 0; int x = 1, z = 1;
            for (int i = 0; i < 5000; i++) {
                x = (x * 1103515245 + 12345) & 0x7fffffff;
                z = (z * 1103515245 + 76543) & 0x7fffffff;
                int wx = x % side, wz = z % side;
                field.sampleInto(wx, wz, out);
                acc += out[0] + out[1];
            }
            if (acc == Long.MIN_VALUE) System.out.print("");
        });
        double sampleMs = s5000 / 1e6;
        double totalMs = solveMs + sampleMs;
        double budgetMs = 50.0; // one server tick @ 20 TPS
        System.out.printf("sample 5000 zombies (per tick)              : %.4f ms%n", sampleMs);
        System.out.printf("flow total for 5000 zombies                 : %.3f ms  (%.1f%% of a 50ms tick)%n",
                totalMs, 100.0 * totalMs / budgetMs);
        System.out.println("NOTE: solve is off-thread (daemon pool) + cached; sample runs on server thread.");
        System.out.println("      This bench isolates the FLOW core only — full per-zombie AI (targeting,");
        System.out.println("      navigation, melee) uses Minecraft classes and is NOT measured here.");
        System.out.println("=====================================================================");
    }
}
