package com.dreykaoas.lethalbreed.effect.contamination;

import com.dreykaoas.lethalbreed.config.domain.ExpertConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
