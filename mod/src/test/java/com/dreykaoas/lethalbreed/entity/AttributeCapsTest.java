package com.dreykaoas.lethalbreed.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The correction factor behind the no-one-shot guarantee. Only the arithmetic is tested here — stamping it
 * onto a live entity needs a server, and the dev harness covers that end.
 */
class AttributeCapsTest {

    @Test
    void aValueUnderItsCapIsLeftAlone() {
        assertEquals(1.0, AttributeCaps.capFactor(10.0, 18.9), 1e-12);
        assertEquals(1.0, AttributeCaps.capFactor(18.9, 18.9), 1e-12);
    }

    @Test
    void aValueOverItsCapIsBroughtExactlyToIt() {
        double actual = 39.6; // phase 14 with a rolled Strength III — the reported one-shot
        double factor = AttributeCaps.capFactor(actual, 18.9);
        assertTrue(factor < 1.0);
        assertEquals(18.9, actual * factor, 1e-9);
    }

    @Test
    void anAbsurdlyHighValueStillLandsOnTheCap() {
        // Phase 1000 reaches five figures on the un-capped curve; the factor must not lose precision there.
        assertEquals(18.9, 11_823.0 * AttributeCaps.capFactor(11_823.0, 18.9), 1e-9);
    }

    @Test
    void degenerateInputCannotProduceInfinityOrASignFlip() {
        assertEquals(1.0, AttributeCaps.capFactor(0.0, 18.9), 1e-12);
        assertEquals(1.0, AttributeCaps.capFactor(-5.0, 18.9), 1e-12);
        // A cap of zero would otherwise zero the attribute outright, which is worse than leaving it alone.
        assertEquals(1.0, AttributeCaps.capFactor(50.0, 0.0), 1e-12);
    }
}
