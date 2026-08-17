package com.dreykaoas.lethalbreed.pack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackAdvanceTest {

    private static final double ARRIVE = 8.0;

    @Test
    void advancesTowardTheDestination() {
        double[] pos = {0.0, 0.0};
        PackAdvance.step(pos, 1000, 0, 0.09, 100, ARRIVE);
        assertEquals(9.0, pos[0], 1e-9);
        assertEquals(0.0, pos[1], 1e-9);
    }

    /**
     * Packs are visited round-robin, so the gap between two visits of the SAME pack grows with the number of
     * packs alive. Without prorating by the ticks actually elapsed, a world with 40 packs would move each of
     * them ten times slower than a world with 4 — a config-invisible speed that depends on the population.
     */
    @Test
    void speedIsIndependentOfHowOftenThePackIsVisited() {
        double[] often = {0.0, 0.0};
        for (int i = 0; i < 10; i++) {
            PackAdvance.step(often, 100_000, 0, 0.09, 20, ARRIVE);
        }
        double[] rarely = {0.0, 0.0};
        PackAdvance.step(rarely, 100_000, 0, 0.09, 200, ARRIVE);
        assertEquals(often[0], rarely[0], 1e-9);
    }

    @Test
    void movesDiagonallyAlongTheStraightLine() {
        double[] pos = {0.0, 0.0};
        PackAdvance.step(pos, 300, 400, 0.5, 100, ARRIVE);
        // 50 blocks travelled along a 3-4-5 hypotenuse: 30 on X, 40 on Z.
        assertEquals(30.0, pos[0], 1e-9);
        assertEquals(40.0, pos[1], 1e-9);
    }

    @Test
    void neverOvershootsTheDestination() {
        double[] pos = {0.0, 0.0};
        // Fast enough to overshoot by a wide margin: the pack must stop at the arrival radius.
        boolean arrived = PackAdvance.step(pos, 100, 0, 10.0, 1000, ARRIVE);
        assertTrue(arrived);
        assertTrue(pos[0] <= 100.0 + 1e-9, "dépassement : " + pos[0]);
        assertEquals(100.0 - ARRIVE, pos[0], 1e-9);
    }

    @Test
    void arrivalIsReportedAtTheArriveDistance() {
        double[] pos = {95.0, 0.0};
        assertTrue(PackAdvance.step(pos, 100, 0, 0.09, 1, ARRIVE));
    }

    @Test
    void stillTravellingIsNotArrival() {
        double[] pos = {0.0, 0.0};
        assertFalse(PackAdvance.step(pos, 1000, 0, 0.09, 100, ARRIVE));
    }

    @Test
    void alreadyThereIsArrivalWithoutMoving() {
        double[] pos = {98.0, 0.0};
        assertTrue(PackAdvance.step(pos, 100, 0, 0.09, 100, ARRIVE));
        assertEquals(98.0, pos[0], 1e-9);
    }

    @Test
    void noElapsedTicksMeansNoMovement() {
        double[] pos = {0.0, 0.0};
        assertFalse(PackAdvance.step(pos, 1000, 0, 0.09, 0, ARRIVE));
        assertEquals(0.0, pos[0], 1e-9);
    }

    /** A clock that went backwards (a /time set, a resumed save) must not teleport the pack backwards. */
    @Test
    void negativeElapsedTicksAreIgnored() {
        double[] pos = {50.0, 0.0};
        PackAdvance.step(pos, 1000, 0, 0.09, -500, ARRIVE);
        assertEquals(50.0, pos[0], 1e-9);
    }

    @Test
    void zeroSpeedNeverArrives() {
        double[] pos = {0.0, 0.0};
        assertFalse(PackAdvance.step(pos, 1000, 0, 0.0, 100_000, ARRIVE));
        assertEquals(0.0, pos[0], 1e-9);
    }

    /** Standing exactly on the destination is a degenerate direction (0/0). It must read as arrived, not
     *  produce NaN coordinates that would then be written to the save file. */
    @Test
    void sittingExactlyOnTheDestinationIsArrivedAndNotNaN() {
        double[] pos = {100.0, 200.0};
        assertTrue(PackAdvance.step(pos, 100, 200, 0.09, 100, ARRIVE));
        assertEquals(100.0, pos[0], 1e-9);
        assertEquals(200.0, pos[1], 1e-9);
    }
}
