package com.dreykaoas.lethalbreed.pack;

import com.dreykaoas.lethalbreed.config.domain.PackConfig;
import com.dreykaoas.lethalbreed.pack.PackWander.Destination;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Destination picking, with a scripted {@link Random} so every draw is known.
 *
 * <p>Same trick as {@code ContaminationRollTest}: the rule takes its randomness as a parameter, so the
 * arithmetic can be checked exactly instead of being sampled and hoped about.
 */
class PackWanderTest {

    private static final int FAR = 1_000_000;

    private final int savedLegMin = PackConfig.packLegMin;
    private final int savedLegMax = PackConfig.packLegMax;
    private final double savedTurn = PackConfig.packTurnDegrees;

    @AfterEach
    void restoreConfig() {
        PackConfig.packLegMin = savedLegMin;
        PackConfig.packLegMax = savedLegMax;
        PackConfig.packTurnDegrees = savedTurn;
    }

    /** A Random whose draws are fixed: {@code d} for every nextDouble, {@code i} for every nextInt. */
    private static Random scripted(double d, int i) {
        return new Random() {
            @Override
            public double nextDouble() {
                return d;
            }

            @Override
            public int nextInt(int bound) {
                return Math.min(i, bound - 1);
            }
        };
    }

    /** nextDouble() == 0.5 maps to a turn of exactly zero, so the heading is carried through untouched. */
    private static Random noTurn(int legDraw) {
        return scripted(0.5, legDraw);
    }

    @Test
    void theLegLengthStaysWithinItsBounds() {
        PackConfig.packLegMin = 96;
        PackConfig.packLegMax = 384;
        for (int draw : new int[] {0, 1, 100, 287, 999_999}) {
            double[] heading = new double[2];
            Destination d = PackWander.next(0, 0, 1, 0, -FAR, -FAR, FAR, FAR, noTurn(draw), heading);
            double len = Math.hypot(d.x(), d.z());
            assertTrue(len >= 96 - 1 && len <= 384 + 1, "step out of bounds: " + len);
        }
    }

    @Test
    void aZeroTurnGivesAPerfectlyStraightLine() {
        PackConfig.packTurnDegrees = 0.0;
        PackConfig.packLegMin = 100;
        PackConfig.packLegMax = 100;
        double[] heading = new double[2];
        // Even on an extreme draw, a zero turn does not deviate.
        Destination d = PackWander.next(0, 0, 1, 0, -FAR, -FAR, FAR, FAR, scripted(1.0, 0), heading);
        assertEquals(100, d.x());
        assertEquals(0, d.z());
        assertEquals(1.0, heading[0], 1e-9);
        assertEquals(0.0, heading[1], 1e-9);
    }

    @Test
    void theTurnNeverExceedsItsLimit() {
        PackConfig.packTurnDegrees = 45.0;
        PackConfig.packLegMin = 100;
        PackConfig.packLegMax = 100;
        for (double draw : new double[] {0.0, 0.25, 0.5, 0.75, 1.0}) {
            double[] heading = new double[2];
            PackWander.next(0, 0, 1, 0, -FAR, -FAR, FAR, FAR, scripted(draw, 0), heading);
            // dot of the outgoing heading with the incoming one (1,0) = cosine of the angle turned through.
            double cos = heading[0];
            assertTrue(cos >= Math.cos(Math.toRadians(45.0)) - 1e-9,
                    "virage de plus de 45° pour un tirage de " + draw + " (cos=" + cos + ")");
        }
    }

    @Test
    void theOutgoingHeadingIsAlwaysUnit() {
        PackConfig.packTurnDegrees = 90.0;
        for (double draw : new double[] {0.0, 0.3, 0.5, 1.0}) {
            double[] heading = new double[2];
            PackWander.next(0, 0, 0.6, 0.8, -FAR, -FAR, FAR, FAR, scripted(draw, 7), heading);
            assertEquals(1.0, Math.hypot(heading[0], heading[1]), 1e-9);
        }
    }

    /** A pack starting with no heading at all (a fresh pack) must still get a usable one, not NaN. */
    @Test
    void aDegenerateHeadingIsRepairedRatherThanPropagated() {
        double[] heading = new double[2];
        Destination d = PackWander.next(0, 0, 0, 0, -FAR, -FAR, FAR, FAR, noTurn(0), heading);
        assertEquals(1.0, Math.hypot(heading[0], heading[1]), 1e-9);
        assertTrue(Double.isFinite(d.x()) && Double.isFinite(d.z()));
    }

    // ---- World border ----

    @Test
    void theDestinationIsClampedInsideTheBorder() {
        PackConfig.packTurnDegrees = 0.0;
        PackConfig.packLegMin = 500;
        PackConfig.packLegMax = 500;
        double[] heading = new double[2];
        Destination d = PackWander.next(0, 0, 1, 0, -200, -200, 200, 200, noTurn(0), heading);
        assertEquals(200, d.x());
    }

    @Test
    void hittingTheBorderBouncesThatAxisOfTheHeading() {
        PackConfig.packTurnDegrees = 0.0;
        PackConfig.packLegMin = 500;
        PackConfig.packLegMax = 500;
        double[] heading = new double[2];
        PackWander.next(0, 0, 1, 0, -200, -200, 200, 200, noTurn(0), heading);
        // Without a bounce the pack would head straight back into the border at every step and stay stuck.
        assertEquals(-1.0, heading[0], 1e-9);
        assertEquals(0.0, heading[1], 1e-9);
    }

    @Test
    void anUntouchedAxisKeepsItsHeading() {
        PackConfig.packTurnDegrees = 0.0;
        PackConfig.packLegMin = 500;
        PackConfig.packLegMax = 500;
        double[] heading = new double[2];
        // Heading due east: X hits the border, Z does not touch it.
        PackWander.next(0, 0, 1, 0, -200, -10_000, 200, 10_000, noTurn(0), heading);
        assertEquals(-1.0, heading[0], 1e-9);
        assertEquals(0.0, heading[1], 1e-9);
    }

    // ---- Reproducibility ----

    @Test
    void theSameSeedAlwaysGivesTheSameDestination() {
        PackConfig.packTurnDegrees = 45.0;
        PackConfig.packLegMin = 96;
        PackConfig.packLegMax = 384;
        double[] a = new double[2];
        double[] b = new double[2];
        Destination first = PackWander.next(10, 20, 0, 1, -FAR, -FAR, FAR, FAR, new Random(4242L), a);
        Destination second = PackWander.next(10, 20, 0, 1, -FAR, -FAR, FAR, FAR, new Random(4242L), b);
        assertEquals(first, second);
        assertEquals(a[0], b[0], 0.0);
        assertEquals(a[1], b[1], 0.0);
    }

    @Test
    void differentSeedsGenerallyDiverge() {
        PackConfig.packTurnDegrees = 45.0;
        PackConfig.packLegMin = 96;
        PackConfig.packLegMax = 384;
        double[] scratch = new double[2];
        Destination a = PackWander.next(0, 0, 0, 1, -FAR, -FAR, FAR, FAR, new Random(1L), scratch);
        Destination b = PackWander.next(0, 0, 0, 1, -FAR, -FAR, FAR, FAR, new Random(2L), scratch);
        assertNotEquals(a, b);
    }

    // ---- Inconsistent configuration ----

    @Test
    void anInvertedLegRangeIsNormalisedInsteadOfCrashing() {
        // The two bounds are independent options; nothing stops an operator from swapping them, and a
        // nextInt(bound <= 0) would crash the server thread. The range is put back the right way round,
        // so the step stays within [100, 400] instead of throwing.
        PackConfig.packLegMin = 400;
        PackConfig.packLegMax = 100;
        double[] heading = new double[2];
        Destination low = PackWander.next(0, 0, 1, 0, -FAR, -FAR, FAR, FAR, noTurn(0), heading);
        Destination high = PackWander.next(0, 0, 1, 0, -FAR, -FAR, FAR, FAR, noTurn(999), heading);
        assertEquals(100, low.x());
        assertEquals(400, high.x());
    }
}
