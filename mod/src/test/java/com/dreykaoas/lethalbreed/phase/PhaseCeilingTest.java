package com.dreykaoas.lethalbreed.phase;

import com.dreykaoas.lethalbreed.config.domain.ProgressionConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ceilings that stop a high-phase zombie from one-shotting a player in full netherite.
 *
 * <p>The load-bearing test here is {@link #phasesUpToTheKneeAreUntouched()}. Every other property is a
 * consequence of the formula; that one is the promise to every existing world that this change adds a bound
 * without re-tuning the game they are already playing.
 */
class PhaseCeilingTest {

    @BeforeEach
    void resetConfig() {
        // Config is shared static state, so a test that moves a ceiling must not leak into the next.
        ProgressionConfig.phaseDmgCeiling = 6.0;
        ProgressionConfig.phaseHpCeiling = 8.0;
        ProgressionConfig.phaseSpdCeiling = 2.2;
        ProgressionConfig.phaseMobcapCeiling = 30.0;
        ProgressionConfig.phaseFrequencyCeiling = 24.0;
    }

    /** The raw, un-capped curve the shipped growth/exponent pairs describe. */
    private static double rawStat(double growth, double exponent, int phase) {
        return 1.0 + growth * Math.pow(phase, exponent);
    }

    @Test
    void phasesUpToTheKneeAreUntouched() {
        for (int p = 0; p <= PhaseConfig.KNEE_PHASE; p++) {
            PhaseConfig.PhaseDef def = PhaseConfig.def(p);
            assertEquals(rawStat(ProgressionConfig.phaseDmgMaxGrowth, ProgressionConfig.phaseDmgExponent, p),
                    def.dmgMax(), 1e-12, "dmgMax changed at phase " + p);
            assertEquals(rawStat(ProgressionConfig.phaseHpMaxGrowth, ProgressionConfig.phaseHpExponent, p),
                    def.hpMax(), 1e-12, "hpMax changed at phase " + p);
            assertEquals(rawStat(ProgressionConfig.phaseSpdMaxGrowth, ProgressionConfig.phaseSpdExponent, p),
                    def.spdMax(), 1e-12, "spdMax changed at phase " + p);
        }
    }

    @Test
    void theCurveJoinsItsCeilingWithoutAStep() {
        // One phase either side of the knee must not jump: the two halves share a value and a slope there.
        double below = PhaseConfig.def(PhaseConfig.KNEE_PHASE).dmgMax();
        double above = PhaseConfig.def(PhaseConfig.KNEE_PHASE + 1).dmgMax();
        double rawStep = rawStat(ProgressionConfig.phaseDmgMaxGrowth, ProgressionConfig.phaseDmgExponent,
                PhaseConfig.KNEE_PHASE + 1)
                - rawStat(ProgressionConfig.phaseDmgMaxGrowth, ProgressionConfig.phaseDmgExponent,
                PhaseConfig.KNEE_PHASE);
        assertTrue(above > below, "curve must still rise past the knee");
        assertTrue(above - below <= rawStep + 1e-9, "capped step must not exceed the raw step");
    }

    @Test
    void everyStatStaysUnderItsCeilingForever() {
        // "Never exceeds", not "never reaches": the curve approaches the ceiling asymptotically in exact
        // arithmetic, but past roughly phase 500 the exponential underflows to zero in a double and the
        // value lands exactly on the ceiling. That is the correct behaviour — what must never happen is
        // crossing it.
        for (int p : new int[] {30, 50, 100, 1_000, 1_000_000}) {
            PhaseConfig.PhaseDef def = PhaseConfig.def(p);
            assertTrue(def.dmgMax() <= ProgressionConfig.phaseDmgCeiling, "dmgMax escaped at phase " + p);
            assertTrue(def.hpMax() <= ProgressionConfig.phaseHpCeiling, "hpMax escaped at phase " + p);
            assertTrue(def.spdMax() <= ProgressionConfig.phaseSpdCeiling, "spdMax escaped at phase " + p);
            assertTrue(PhaseTable.mobcap(p) <= ProgressionConfig.phaseMobcapCeiling, "mobcap escaped at " + p);
            assertTrue(PhaseTable.frequency(p) <= ProgressionConfig.phaseFrequencyCeiling, "freq escaped at " + p);
        }
        // Within the phase range anyone will actually play, it is still strictly below.
        assertTrue(PhaseConfig.def(100).dmgMax() < ProgressionConfig.phaseDmgCeiling);
    }

    @Test
    void theCurveKeepsRisingSoHighPhasesStayDistinct() {
        // A hard clamp was rejected precisely because it makes every phase past the crossing identical.
        assertTrue(PhaseConfig.def(50).dmgMax() > PhaseConfig.def(30).dmgMax());
        assertTrue(PhaseConfig.def(100).dmgMax() > PhaseConfig.def(50).dmgMax());
    }

    @Test
    void theMinCurveNeverOvertakesTheMaxCurve() {
        // Both ends of a roll range share one ceiling; if they did not, the min could cross the max and
        // roll(min, max) would produce a reversed range.
        for (int p : new int[] {0, 15, 30, 100, 100_000}) {
            PhaseConfig.PhaseDef def = PhaseConfig.def(p);
            assertTrue(def.dmgMin() <= def.dmgMax(), "dmg range inverted at phase " + p);
            assertTrue(def.hpMin() <= def.hpMax(), "hp range inverted at phase " + p);
            assertTrue(def.spdMin() <= def.spdMax(), "spd range inverted at phase " + p);
        }
    }

    @Test
    void aCeilingBelowTheKneeFlattensInsteadOfInverting() {
        // Misconfiguration, not a crash: a ceiling under the knee cannot be approached from below, so the
        // curve pins at the knee rather than returning something below 1.0 or NaN.
        ProgressionConfig.phaseDmgCeiling = 1.0;
        double knee = rawStat(ProgressionConfig.phaseDmgMaxGrowth, ProgressionConfig.phaseDmgExponent,
                PhaseConfig.KNEE_PHASE);
        assertEquals(knee, PhaseConfig.def(1_000).dmgMax(), 1e-12);
    }

    @Test
    void softCapIsContinuousAtTheKnee() {
        assertEquals(3.0, PhaseConfig.softCap(3.0, 3.0, 6.0), 1e-12);
        assertEquals(2.0, PhaseConfig.softCap(2.0, 3.0, 6.0), 1e-12);
    }
}
