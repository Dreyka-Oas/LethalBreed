package com.dreykaoas.lethalbreed.ai.flowfield;


import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless pins for the shared {@link Neighbors8} geometry, now backing both the CPU solver and the optimality
 * checker. Same package for package-private access. No Minecraft types touched.
 */
class Neighbors8Test {

    @Test
    void eightOffsetsFourOrthogonalFourDiagonal() {
        assertEquals(8, Neighbors8.DX.length);
        assertEquals(8, Neighbors8.DZ.length);
        int diag = 0;
        for (int k = 0; k < 8; k++) {
            if (Neighbors8.isDiagonal(k)) diag++;
            assertTrue(Math.abs(Neighbors8.DX[k]) <= 1 && Math.abs(Neighbors8.DZ[k]) <= 1);
            assertFalse(Neighbors8.DX[k] == 0 && Neighbors8.DZ[k] == 0, "no zero (self) offset");
        }
        assertEquals(4, diag, "exactly four diagonal neighbours");
    }

    @Test
    void orthogonalStepsNeverCornerBlocked() {
        boolean[] pass = {true, true, true, true}; // 2x2, depth 2
        for (int k = 0; k < 8; k++) {
            if (!Neighbors8.isDiagonal(k)) {
                assertFalse(Neighbors8.cornerBlocked(pass, 0, 0, Neighbors8.DX[k] < 0 ? 0 : Neighbors8.DX[k],
                        Neighbors8.DZ[k] < 0 ? 0 : Neighbors8.DZ[k], 2, k));
            }
        }
    }

    @Test
    void diagonalBlockedWhenEitherOrthogonalCornerSolid() {
        int depth = 2;
        int k = 4; // (1,1) diagonal from (0,0) → (1,1)
        assertTrue(Neighbors8.isDiagonal(k));
        // Both corners passable → allowed.
        assertFalse(Neighbors8.cornerBlocked(new boolean[]{true, true, true, true}, 0, 0, 1, 1, depth, k));
        // Corner (cx,nz)=(0,1) index 1 solid → blocked.
        assertTrue(Neighbors8.cornerBlocked(new boolean[]{true, false, true, true}, 0, 0, 1, 1, depth, k));
        // Corner (nx,cz)=(1,0) index 2 solid → blocked.
        assertTrue(Neighbors8.cornerBlocked(new boolean[]{true, true, false, true}, 0, 0, 1, 1, depth, k));
    }
}
