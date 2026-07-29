package com.dreykaoas.lethalbreed.entity.mood;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless characterization of the pure part of {@link ShelterFinder}. The search itself needs a live
 * ServerLevel and cannot run here; what CAN be checked is the finite score ceiling that makes the two
 * prune branches active from the first column instead of inert until a first hit (audit #6).
 */
class ShelterFinderTest {

    @Test
    void maxScoreAdmitsTheFurthestLegalCandidate() {
        // The worst acceptable candidate sits at the corner of the horizontal square AND the edge of the
        // vertical band, so the ceiling must be at least its score or the search would prune a valid result.
        int radius = 12;
        int vBand = Math.max(4, radius / 2);
        double worstLegal = (double) radius * radius + (double) radius * radius + 3.0 * vBand * vBand;
        assertTrue(ShelterFinder.maxScore(radius) > worstLegal,
                "ceiling must strictly exceed the furthest legal candidate, else it is pruned away");
    }

    @Test
    void maxScoreIsFiniteSoPruningIsActiveImmediately() {
        assertTrue(Double.isFinite(ShelterFinder.maxScore(12)));
        assertTrue(Double.isFinite(ShelterFinder.maxScore(1)));
        assertTrue(Double.isFinite(ShelterFinder.maxScore(64)));
    }

    @Test
    void maxScoreGrowsWithRadius() {
        assertTrue(ShelterFinder.maxScore(24) > ShelterFinder.maxScore(12));
    }

    @Test
    void maxScoreHandlesADegenerateRadius() {
        assertTrue(Double.isFinite(ShelterFinder.maxScore(0)));
        assertEquals(ShelterFinder.maxScore(0), ShelterFinder.maxScore(0));
    }
}
