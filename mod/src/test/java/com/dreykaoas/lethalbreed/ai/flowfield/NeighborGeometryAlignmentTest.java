package com.dreykaoas.lethalbreed.ai.flowfield;

import com.dreykaoas.lethalbreed.dev.compute.FlowFieldSelfChecks;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code Neighbors8} is package-private final in {@code src/main} — correctly, since nothing outside its own
 * package needs the 8-neighbour geometry (see its class javadoc). {@code FlowFieldSelfChecks}, the dev-only
 * optimality oracle used by {@code ComputeSelfTest}, therefore cannot read it and instead re-derives its own
 * {@code DX}/{@code DZ}/{@code isDiagonal} independently (see its class javadoc for why re-deriving, rather
 * than sharing code with the thing being checked, is the point of an oracle). That independence is exactly
 * what makes the two copies able to drift silently: if either {@code Neighbors8} or {@code
 * FlowFieldSelfChecks} ever reorders or edits its arrays, nothing else notices, and {@code ComputeSelfTest}
 * starts validating flow fields against the wrong geometry without a single compile error.
 *
 * <p>This test can only exist here, in {@code src/test}, package {@code
 * com.dreykaoas.lethalbreed.ai.flowfield}: that package placement is what makes the package-private {@code
 * Neighbors8} visible, and {@code build.gradle.kts} puts the {@code dev} source set's output on the test
 * classpath — see the comment above {@code sourceSets.test} there — which is what makes {@code
 * FlowFieldSelfChecks} (via {@code dev}) visible from the same test alongside it.
 *
 * <p><b>{@code cornerBlocked} is pinned too.</b> It is the fourth member {@code Neighbors8} and {@code
 * FlowFieldSelfChecks} both duplicate (alongside {@code DX}/{@code DZ}/{@code isDiagonal}), and the no-corner-
 * cutting rule is exactly the kind of thing a future edit could change in one copy and forget in the other.
 * {@code Neighbors8.cornerBlocked} stays package-private (this test already shares its package, so that costs
 * nothing); {@code FlowFieldSelfChecks.cornerBlocked} was widened from {@code private} to {@code public} to
 * make it reachable here — free, since {@code src/dev} never ships.
 */
class NeighborGeometryAlignmentTest {

    @Test
    void bothGeometryTablesAreLengthEight() {
        assertEquals(8, Neighbors8.DX.length, "Neighbors8.DX length drifted");
        assertEquals(8, Neighbors8.DZ.length, "Neighbors8.DZ length drifted");
        assertEquals(8, FlowFieldSelfChecks.DX.length, "FlowFieldSelfChecks.DX length drifted");
        assertEquals(8, FlowFieldSelfChecks.DZ.length, "FlowFieldSelfChecks.DZ length drifted");
    }

    @Test
    void everyIndexMatchesBetweenNeighbors8AndTheDevOracle() {
        for (int k = 0; k < 8; k++) {
            assertEquals(Neighbors8.DX[k], FlowFieldSelfChecks.DX[k], "DX[" + k + "] drifted");
            assertEquals(Neighbors8.DZ[k], FlowFieldSelfChecks.DZ[k], "DZ[" + k + "] drifted");
            assertEquals(Neighbors8.isDiagonal(k), FlowFieldSelfChecks.isDiagonal(k),
                    "isDiagonal(" + k + ") drifted");
        }
    }

    /**
     * Drive both {@code cornerBlocked} copies with identical small passable-grids and assert they agree for
     * every neighbour index, including the corner-cut cases (where the two orthogonally-adjacent cells are
     * mixed passable/impassable). A 3x3 grid centred on (1,1) puts every one of the 8 neighbours in bounds.
     */
    @Test
    void cornerBlockedAgreesBetweenNeighbors8AndTheDevOracleForEveryPassableGrid() {
        int depth = 3;
        int cx = 1, cz = 1;

        // Fixture grids: index = x*depth+z over a 3x3 area. Each exercises a different mix of passable/
        // impassable orthogonal cells around the center, so every diagonal's two "shoulder" cells hit both
        // true/false combinations across the fixtures as a whole.
        boolean[][] grids = {
                allTrue(depth * depth),          // nothing blocks anything
                allFalse(depth * depth),          // everything blocks every diagonal's shoulders
                shoulders(depth, true, false),    // one shoulder passable, the other not (asymmetric)
                shoulders(depth, false, true),    // the mirror of the above
        };

        for (boolean[] passable : grids) {
            for (int k = 0; k < 8; k++) {
                int nx = cx + Neighbors8.DX[k];
                int nz = cz + Neighbors8.DZ[k];
                boolean expected = Neighbors8.cornerBlocked(passable, cx, cz, nx, nz, depth, k);
                boolean actual = FlowFieldSelfChecks.cornerBlocked(passable, cx, cz, nx, nz, depth, k);
                assertEquals(expected, actual,
                        "cornerBlocked(k=" + k + ") drifted for grid " + java.util.Arrays.toString(passable));
            }
        }
    }

    private static boolean[] allTrue(int n) {
        boolean[] a = new boolean[n];
        java.util.Arrays.fill(a, true);
        return a;
    }

    private static boolean[] allFalse(int n) {
        return new boolean[n]; // defaults to false
    }

    /** All passable except the two orthogonal "shoulder" cells north (x, z-1) and east (x+1, z) of the
     *  center, set independently, so a diagonal step toward the north-east corner exercises a mixed case. */
    private static boolean[] shoulders(int depth, boolean north, boolean east) {
        boolean[] a = allTrue(depth * depth);
        a[1 * depth + 0] = north; // (1,0)
        a[2 * depth + 1] = east;  // (2,1)
        return a;
    }
}
