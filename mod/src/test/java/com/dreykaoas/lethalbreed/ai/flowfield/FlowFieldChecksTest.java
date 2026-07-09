package com.dreykaoas.lethalbreed.ai.flowfield;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless characterization of {@link FlowFieldChecks} against fields produced by the pure CPU solver. Same
 * package so it can build the package-private {@link Snapshot}. No Minecraft types touched.
 */
class FlowFieldChecksTest {

    /** Flat open WxD grid with the seed in a corner. */
    private static Snapshot openGrid(int w, int d, int seedX, int seedZ) {
        int n = w * d;
        boolean[] passable = new boolean[n];
        for (int i = 0; i < n; i++) {
            passable[i] = true;
        }
        int[] extraCost = new int[n];
        byte[] flags = new byte[n];
        int[] seedCells = {seedX * d + seedZ};
        return new Snapshot(0, 0, w, d, 64, passable, extraCost, flags, seedCells);
    }

    @Test
    void seedCellIsZeroAndFarCellReachable() {
        Snapshot s = openGrid(16, 16, 0, 0);
        FlowField f = CpuFlowField.compute(s);
        assertEquals(0, f.costAt(0, 0), "seed cost must be 0");
        assertTrue(f.costAt(15, 15) > 0, "far corner reachable & positive");
        assertTrue(f.costAt(15, 15) < FlowField.IMPASSABLE, "far corner not impassable");
    }

    @Test
    void solvedFieldPassesAllChecks() {
        Snapshot s = openGrid(24, 24, 0, 0);
        FlowField f = CpuFlowField.compute(s);
        assertTrue(FlowFieldChecks.cpuSanity(f, 24, 24));
        assertTrue(FlowFieldChecks.directionsDescend(f, 24, 24), "every reachable cell steps downhill");
        assertTrue(FlowFieldChecks.costFieldOptimal(s, f), "cost field is the Bellman fixpoint");
    }

    @Test
    void identicalFieldsCompareEqual() {
        Snapshot s = openGrid(16, 16, 0, 0);
        FlowField a = CpuFlowField.compute(s);
        FlowField b = CpuFlowField.compute(s);
        assertEquals(0, FlowFieldChecks.compareCost(s, a, b)[0], "same snapshot ⇒ identical fields");
    }

    @Test
    void differentSeedsProduceMismatch() {
        Snapshot s0 = openGrid(16, 16, 0, 0);
        Snapshot s1 = openGrid(16, 16, 15, 15);
        FlowField a = CpuFlowField.compute(s0);
        FlowField b = CpuFlowField.compute(s1);
        int[] diff = FlowFieldChecks.compareCost(s0, a, b);
        assertTrue(diff[0] > 0, "fields seeded at opposite corners must differ");
    }

    @Test
    void orthogonalStepCostsOne() {
        // With default ortho cost on an open grid, the cell next to the seed is exactly one step out.
        Snapshot s = openGrid(8, 8, 0, 0);
        FlowField f = CpuFlowField.compute(s);
        int orth = Math.max(1, com.dreykaoas.lethalbreed.config.domain.FlowConfig.flowOrthoCost);
        assertEquals(orth, f.costAt(1, 0), "one orthogonal step from the seed");
        assertEquals(orth, f.costAt(0, 1), "one orthogonal step from the seed");
    }

    @Test
    void sampleDirectionDescendsFromFarCorner() {
        Snapshot s = openGrid(8, 8, 0, 0);
        FlowField f = CpuFlowField.compute(s);
        int[] dir = new int[2];
        assertTrue(f.sampleInto(7, 7, dir), "far corner yields a direction");
        // stepping along the sampled direction must reduce cost
        int here = f.costAt(7, 7);
        assertTrue(f.costAt(7 + dir[0], 7 + dir[1]) < here, "sampled step is strictly cheaper");
        assertTrue(dir[0] <= 0 && dir[1] <= 0, "direction points back toward the corner seed");
    }
}
