package com.dreykaoas.lethalbreed.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Headless characterization of {@link ConfigBounds#clamp} — the central guard for every config entry point. */
class ConfigBoundsTest {

    @Test
    void clampsIntegerIntoRange() {
        // tickBuckets bounds = [1, 1000]
        assertEquals(1, ConfigBounds.clamp("tickBuckets", -5));
        assertEquals(1000, ConfigBounds.clamp("tickBuckets", 99999));
        assertEquals(8, ConfigBounds.clamp("tickBuckets", 8));
    }

    @Test
    void clampIsCaseInsensitive() {
        assertEquals(1, ConfigBounds.clamp("TICKBUCKETS", 0));
        assertEquals(1, ConfigBounds.clamp("TickBuckets", 0));
    }

    @Test
    void nonFiniteFloatPulledToLowerBound() {
        // navSpeed bounds = [0, 10]
        assertEquals(0f, (Float) ConfigBounds.clamp("navSpeed", Float.NaN));
        assertEquals(0f, (Float) ConfigBounds.clamp("navSpeed", Float.POSITIVE_INFINITY));
    }

    @Test
    void nonFiniteDoublePulledToLowerBound() {
        // breakProgressPerTick bounds = [0.001, 1.0]
        assertEquals(0.001, (Double) ConfigBounds.clamp("breakProgressPerTick", Double.NaN));
    }

    @Test
    void finiteDoubleClampedBothWays() {
        assertEquals(1.0, (Double) ConfigBounds.clamp("breakProgressPerTick", 5.0));
        assertEquals(0.001, (Double) ConfigBounds.clamp("breakProgressPerTick", -1.0));
        assertEquals(0.5, (Double) ConfigBounds.clamp("breakProgressPerTick", 0.5));
    }

    @Test
    void unboundedFieldPassesThroughUnchanged() {
        Object v = 123456;
        assertSame(v, ConfigBounds.clamp("noSuchFieldEver", v));
    }

    @Test
    void booleanPassesThroughUnchanged() {
        Object v = Boolean.TRUE;
        // even for a bounded-ish name, a boolean is never numerically clamped
        assertSame(v, ConfigBounds.clamp("tickBuckets", v));
    }
}
