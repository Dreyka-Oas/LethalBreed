package com.dreykaoas.lethalbreed.ai.flowfield;


import com.dreykaoas.lethalbreed.config.domain.engine.FlowConfig;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The direction field must not merely descend — it must REALIZE each cell's converged cost.
 *
 * <p>Merely descending only asserts {@code cost[next] < cost[here]}, which any downhill step satisfies.
 * That is weaker than correctness: the relaxation defines
 * {@code cost[i] = min over n of (cost[n] + step(i,n) + extra[i])}, so the emitted direction must point at
 * a neighbour that ACHIEVES that minimum. A step that descends but does not realize the cost sends the
 * zombie on a strictly longer route than the field it is reading claims.
 *
 * <p>This matters because the two solvers pick directions by different criteria: the GPU kernel records
 * {@code argmin(cost[n] + step)} as a by-product of relaxing, while the CPU runs a separate pass choosing
 * {@code argmin(cost[n])} — ignoring the step cost. With the default {@code flowOrthoCost=10} /
 * {@code flowDiagonalCost=14} those two differ whenever a diagonal neighbour is cheaper than an orthogonal
 * one by less than {@code diag - ortho}, so a GPU-less machine can path differently from a GPU one.
 */
class FlowFieldDirectionOptimalTest {

    /** Step cost for the offset (dx,dz), using the same config values both solvers read. */
    private static int step(int dx, int dz) {
        int orth = Math.max(1, FlowConfig.flowOrthoCost);
        int diag = Math.max(orth, FlowConfig.flowDiagonalCost);
        return (dx != 0 && dz != 0) ? diag : orth;
    }

    /**
     * Assert every reachable non-seed cell's direction realizes its cost. Returns the number of cells
     * checked so a vacuous pass (nothing reachable) can't masquerade as a green test.
     */
    private static int assertDirectionsRealizeCost(Snapshot s, FlowField f, String what) {
        int[] dir = new int[2];
        int checked = 0;
        for (int cx = 0; cx < s.width; cx++) {
            for (int cz = 0; cz < s.depth; cz++) {
                int here = f.costAt(cx, cz);
                if (here <= 0 || here >= FlowField.IMPASSABLE) {
                    continue; // seed or unreachable: no direction expected
                }
                if (!f.sampleInto(cx, cz, dir)) {
                    continue;
                }
                int target = f.costAt(cx + dir[0], cz + dir[1]);
                int realized = target + step(dir[0], dir[1]) + s.extraCost[cx * s.depth + cz];
                assertEquals(here, realized,
                        what + ": cell (" + cx + "," + cz + ") cost=" + here + " but its direction ("
                                + dir[0] + "," + dir[1] + ") reaches cost=" + target + " for a realized "
                                + realized + " — the step descends but does not achieve the cell's own cost");
                checked++;
            }
        }
        return checked;
    }

    /** Flat open grid, seed in a corner — the simplest field where diagonal and orthogonal steps compete. */
    @Test
    void openGridDirectionsRealizeTheirCost() {
        int w = 24, d = 24, n = w * d;
        boolean[] passable = new boolean[n];
        java.util.Arrays.fill(passable, true);
        Snapshot s = new Snapshot(0, 0, w, d, 64, passable, new int[n], new byte[n], new int[]{0});
        FlowField f = CpuFlowField.compute(s);
        int checked = assertDirectionsRealizeCost(s, f, "open grid");
        org.junit.jupiter.api.Assertions.assertTrue(checked > 400, "expected a populated field, checked=" + checked);
    }

    /**
     * Random obstacle mazes. Walls create the neighbourhoods where a diagonal neighbour is cheaper than an
     * orthogonal one by less than {@code diag - ortho} — the exact shape that separates the two criteria.
     */
    @Test
    void obstacleGridDirectionsRealizeTheirCost() {
        int w = 32, d = 32, n = w * d;
        int checkedTotal = 0;
        for (int trial = 0; trial < 40; trial++) {
            Random rng = new Random(1234L + trial); // fixed seeds: a failure is always reproducible
            boolean[] passable = new boolean[n];
            for (int i = 0; i < n; i++) {
                passable[i] = rng.nextInt(100) >= 28; // ~28% walls: broken up, still widely connected
            }
            passable[0] = true; // keep the seed cell open
            Snapshot s = new Snapshot(0, 0, w, d, 64, passable, new int[n], new byte[n], new int[]{0});
            FlowField f = CpuFlowField.compute(s);
            checkedTotal += assertDirectionsRealizeCost(s, f, "obstacle grid trial " + trial);
        }
        org.junit.jupiter.api.Assertions.assertTrue(checkedTotal > 5000,
                "expected populated fields across trials, checked=" + checkedTotal);
    }

    /** Break/build cells carry a non-zero enter cost, which the realization identity must account for. */
    @Test
    void extraCostGridDirectionsRealizeTheirCost() {
        int w = 24, d = 24, n = w * d;
        boolean[] passable = new boolean[n];
        java.util.Arrays.fill(passable, true);
        int[] extra = new int[n];
        Random rng = new Random(99L);
        for (int i = 0; i < n; i++) {
            if (rng.nextInt(100) < 20) {
                extra[i] = 40 + rng.nextInt(60); // breakable/buildable cells cost extra to enter
            }
        }
        Snapshot s = new Snapshot(0, 0, w, d, 64, passable, extra, new byte[n], new int[]{0});
        FlowField f = CpuFlowField.compute(s);
        int checked = assertDirectionsRealizeCost(s, f, "extra-cost grid");
        org.junit.jupiter.api.Assertions.assertTrue(checked > 400, "expected a populated field, checked=" + checked);
    }
}
