package com.dreykaoas.lethalbreed.ai.flowfield;

import com.dreykaoas.lethalbreed.config.domain.FlowConfig;
import org.junit.jupiter.api.Test;

import java.util.Random;

/**
 * Measurement harness (not an assertion test): quantifies what the direction-criterion fix actually bought,
 * by re-deriving BOTH criteria from the same converged cost field and comparing them cell by cell.
 *
 * <p>"old" = argmin(neighbourCost), the criterion the CPU direction pass used.
 * <br>"new" = argmin(neighbourCost + stepCost), what the relaxation and the GPU kernel minimise.
 *
 * <p>Prints a table to stdout; run with {@code --info} to see it. Kept in the suite so the numbers can be
 * re-derived on demand rather than trusted from a commit message.
 */
class RoutingQualityMeasureTest {

    private static int step(int k) {
        int orth = Math.max(1, FlowConfig.flowOrthoCost);
        int diag = Math.max(orth, FlowConfig.flowDiagonalCost);
        return Neighbors8.isDiagonal(k) ? diag : orth;
    }

    /** Per-grid tally: {cells with a direction, cells where old != new, total excess cost of old}. */
    private static long[] compare(Snapshot s, FlowField f) {
        long cells = 0, divergent = 0, excess = 0;
        for (int cx = 0; cx < s.width; cx++) {
            for (int cz = 0; cz < s.depth; cz++) {
                int i = cx * s.depth + cz;
                int here = f.costAt(cx, cz);
                if (here <= 0 || here >= FlowField.IMPASSABLE) {
                    continue;
                }
                int oldBestCost = Integer.MAX_VALUE, oldTotal = 0;
                int newBestTotal = Integer.MAX_VALUE;
                int oldK = -1, newK = -1;
                for (int k = 0; k < 8; k++) {
                    int nx = cx + Neighbors8.DX[k];
                    int nz = cz + Neighbors8.DZ[k];
                    if (nx < 0 || nx >= s.width || nz < 0 || nz >= s.depth) {
                        continue;
                    }
                    int ni = nx * s.depth + nz;
                    if (f.costAt(nx, nz) >= FlowField.IMPASSABLE) {
                        continue;
                    }
                    if (Neighbors8.cornerBlocked(s.passable, cx, cz, nx, nz, s.depth, k)) {
                        continue;
                    }
                    int nc = f.costAt(nx, nz);
                    if (nc < oldBestCost) {
                        oldBestCost = nc;
                        oldTotal = nc + step(k);
                        oldK = k;
                    }
                    if (nc + step(k) < newBestTotal) {
                        newBestTotal = nc + step(k);
                        newK = k;
                    }
                }
                if (oldK < 0 || newK < 0) {
                    continue;
                }
                cells++;
                if (oldTotal != newBestTotal) {
                    divergent++;
                    excess += oldTotal - newBestTotal;
                }
            }
        }
        return new long[]{cells, divergent, excess};
    }

    private static Snapshot obstacleGrid(int w, int d, long seed, int wallPct) {
        int n = w * d;
        Random rng = new Random(seed);
        boolean[] passable = new boolean[n];
        for (int i = 0; i < n; i++) {
            passable[i] = rng.nextInt(100) >= wallPct;
        }
        passable[0] = true;
        return new Snapshot(0, 0, w, d, 64, passable, new int[n], new byte[n], new int[]{0});
    }

    @Test
    void measureDirectionCriterionImpact() {
        System.out.println("\n=== Qualité de routage : ancien critère argmin(coût) vs nouveau argmin(coût+pas) ===");
        System.out.printf("%-26s %10s %12s %10s %14s%n",
                "Terrain", "cellules", "divergentes", "%", "surcoût moyen");
        int[] wallPcts = {0, 10, 20, 28, 35};
        for (int wallPct : wallPcts) {
            long cells = 0, divergent = 0, excess = 0;
            for (int trial = 0; trial < 25; trial++) {
                Snapshot s = obstacleGrid(64, 64, 777L + trial, wallPct);
                long[] r = compare(s, CpuFlowField.compute(s));
                cells += r[0];
                divergent += r[1];
                excess += r[2];
            }
            double pct = cells == 0 ? 0 : 100.0 * divergent / cells;
            double avg = divergent == 0 ? 0 : (double) excess / divergent;
            System.out.printf("%-26s %10d %12d %9.1f%% %14.2f%n",
                    wallPct + "% de murs", cells, divergent, pct, avg);
        }
        System.out.println("(surcoût moyen = coût de pas gaspillé par cellule divergente, "
                + "en unités où un pas droit vaut " + FlowConfig.flowOrthoCost + ")");
    }
}
