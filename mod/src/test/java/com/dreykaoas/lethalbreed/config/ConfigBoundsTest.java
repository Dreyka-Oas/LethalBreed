package com.dreykaoas.lethalbreed.config;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void nonFiniteOnUnboundedFieldIsRejected() {
        // A field with no bounds entry used to let NaN/Infinity through untouched — and some of them land
        // in a persistent per-entity attachment, so a later config fix can't undo the damage. clamp() now
        // refuses rather than pass a poison value through; ConfigAccess.apply turns this into `false` and
        // the field keeps its previous valid value.
        assertThrows(IllegalArgumentException.class,
                () -> ConfigBounds.clamp("noSuchFieldEver", Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> ConfigBounds.clamp("noSuchFieldEver", Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class,
                () -> ConfigBounds.clamp("noSuchFieldEver", Float.NaN));
    }

    @Test
    void finiteValueOnUnboundedFieldStillPassesThrough() {
        Object v = 3.5;
        assertSame(v, ConfigBounds.clamp("noSuchFieldEver", v));
    }

    @Test
    void booleanPassesThroughUnchanged() {
        Object v = Boolean.TRUE;
        // even for a bounded-ish name, a boolean is never numerically clamped
        assertSame(v, ConfigBounds.clamp("tickBuckets", v));
    }

    /**
     * Every scalar-numeric config option MUST have a bounds entry. This is the regression guard for audit
     * finding #6: a whole subsystem (contamination levels/evolution/episodes) was added after the table was
     * written, so 27+ numeric fields silently fell through unbounded — one of them into an instant-death
     * damage branch. A field without an entry gets no range clamp at all (only the non-finite check), so
     * this test fails loudly the next time a numeric option is added without a bound, listing exactly which.
     * Booleans and {@code double[]} are exempt: neither is Range-clamped by design.
     */
    @Test
    void everyScalarNumericOptionHasBounds() {
        List<String> missing = new ArrayList<>();
        for (Field f : ConfigSchema.all()) {
            Class<?> t = f.getType();
            boolean scalarNumeric = t == int.class || t == long.class || t == double.class || t == float.class;
            if (scalarNumeric && ConfigBoundsTable.get(f.getName()) == null) {
                missing.add(f.getName());
            }
        }
        assertTrue(missing.isEmpty(),
                "these numeric config options have no bounds entry (add them to ConfigBoundsTable): " + missing);
    }
}
