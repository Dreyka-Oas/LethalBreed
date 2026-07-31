package com.dreykaoas.lethalbreed.effect.contamination;

import com.dreykaoas.lethalbreed.config.domain.ExpertConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Headless characterization of {@link ContaminationRoll} — the plague's random-draw rules.
 *
 * <p>This class exists so these rules ARE testable: they used to live on ContaminationState, whose
 * static initialiser registers Fabric attachment types and therefore cannot load under plain JUnit.
 * Findings #5 and #12 are both "the same arithmetic, written twice, drifted apart" — untested
 * arithmetic is exactly how that happens.
 */
class ContaminationRollTest {

    private final double savedFloor = ExpertConfig.expertContamIntensityFloor;

    @AfterEach
    void restoreConfig() {
        ExpertConfig.expertContamIntensityFloor = savedFloor;
    }

    @Test
    void intensityFactorInvertsTheMultiplier() {
        assertEquals(0.5, ContaminationRoll.intensityFactor(2.0), 1e-12);
        assertEquals(0.25, ContaminationRoll.intensityFactor(4.0), 1e-12);
        assertEquals(1.0, ContaminationRoll.intensityFactor(1.0), 1e-12);
    }

    @Test
    void intensityFactorHonoursTheConfiguredFloor() {
        // This is finding #5: the hallucination flare used a hardcoded 1.0e-3 while episodes used
        // this option, so tuning it moved 3 of the 4 flare types and silently skipped the fourth.
        ExpertConfig.expertContamIntensityFloor = 0.5;
        assertEquals(2.0, ContaminationRoll.intensityFactor(0.1), 1e-12);
        assertEquals(2.0, ContaminationRoll.intensityFactor(0.0), 1e-12);

        ExpertConfig.expertContamIntensityFloor = 0.01;
        assertEquals(100.0, ContaminationRoll.intensityFactor(0.001), 1e-12);
    }

    @Test
    void intensityFactorNeverDividesByZero() {
        ExpertConfig.expertContamIntensityFloor = 1.0e-6;
        assertTrue(Double.isFinite(ContaminationRoll.intensityFactor(0.0)));
        assertTrue(Double.isFinite(ContaminationRoll.intensityFactor(-5.0)));
    }

    /** A Random that hands back a scripted sequence, so a draw's arithmetic is checked exactly. */
    private static java.util.Random scripted(double... values) {
        return new java.util.Random() {
            private int i = 0;
            @Override
            public double nextDouble() {
                return values[i++ % values.length];
            }
        };
    }

    @Test
    void uniformLerpsAcrossTheRange() {
        assertEquals(10.0, ContaminationRoll.uniform(scripted(0.0), 10.0, 20.0), 1e-12);
        assertEquals(20.0, ContaminationRoll.uniform(scripted(1.0), 10.0, 20.0), 1e-12);
        assertEquals(15.0, ContaminationRoll.uniform(scripted(0.5), 10.0, 20.0), 1e-12);
    }

    @Test
    void uniformReordersAnInvertedRange() {
        // This is finding #12: the five hand-written copies did the lerp WITHOUT this reorder, so an
        // operator who typed min=5, max=1 (both individually in bounds — ConfigBoundsTable bounds each
        // field alone, never the relation between two) got a draw BELOW the minimum.
        assertEquals(1.0, ContaminationRoll.uniform(scripted(0.0), 5.0, 1.0), 1e-12);
        assertEquals(5.0, ContaminationRoll.uniform(scripted(1.0), 5.0, 1.0), 1e-12);
        assertEquals(3.0, ContaminationRoll.uniform(scripted(0.5), 5.0, 1.0), 1e-12);
    }

    @Test
    void uniformFloorsANegativeMinimumAtZero() {
        assertEquals(0.0, ContaminationRoll.uniform(scripted(0.0), -8.0, 4.0), 1e-12);
        assertEquals(4.0, ContaminationRoll.uniform(scripted(1.0), -8.0, 4.0), 1e-12);
        // both ends negative → collapses to a constant 0, never a negative draw
        assertEquals(0.0, ContaminationRoll.uniform(scripted(0.5), -8.0, -2.0), 1e-12);
    }

    @Test
    void percentComparesASecondRollAgainstTheDrawnThreshold() {
        // first nextDouble picks the threshold in [min,max], second is the roll compared to it
        assertTrue(ContaminationRoll.percent(scripted(1.0, 0.05), 10.0, 20.0));   // threshold 20%, roll 5%
        assertFalse(ContaminationRoll.percent(scripted(0.0, 0.5), 10.0, 20.0));   // threshold 10%, roll 50%
    }

    @Test
    void percentAtZeroNeverFires() {
        assertFalse(ContaminationRoll.percent(scripted(0.5, 0.0), 0.0, 0.0));
    }
}
