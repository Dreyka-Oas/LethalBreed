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
}
