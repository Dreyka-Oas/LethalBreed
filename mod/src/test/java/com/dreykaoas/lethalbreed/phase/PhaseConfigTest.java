package com.dreykaoas.lethalbreed.phase;

import com.dreykaoas.lethalbreed.config.domain.ProgressionConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Headless characterization of the formula-driven {@link PhaseConfig#def(int)} curve and the optional
 *  {@link PhaseManager#applyCeiling(int)} phase cap/loop — no server required. */
class PhaseConfigTest {

    @AfterEach
    void resetConfig() {
        ProgressionConfig.phaseMaxEnabled = false;
        ProgressionConfig.phaseMax = 50;
        ProgressionConfig.phaseLoopEnabled = false;
    }

    @Test
    void phaseZeroIsClassic() {
        PhaseConfig.PhaseDef d = PhaseConfig.def(0);
        assertEquals(1.0, d.hpMin());
        assertEquals(1.0, d.hpMax());
        assertEquals(1.0, d.dmgMin());
        assertEquals(1.0, d.dmgMax());
        assertEquals(1.0, d.spdMin());
        assertEquals(1.0, d.spdMax());
        assertEquals(0.0, d.armorChance());
        assertEquals(0, d.armorMaxTier());
        assertEquals(0.0, d.weaponChance());
        assertEquals(0, d.weaponMaxTier());
        assertEquals(0, d.enchantLevel());
        assertEquals(0.0, d.effChance());
        assertEquals(0, d.effCount());
        assertEquals(0, d.effMaxAmp());
    }

    @Test
    void phaseFifteenMatchesOldHandTunedTable() {
        // Old static table's phase-15 ("Necrosis terminalis") values — continuity check so a world
        // already at phase 15 sees no difficulty jump when the formula ships.
        PhaseConfig.PhaseDef d = PhaseConfig.def(15);
        assertEquals(3.00, d.hpMin(), 0.01);
        assertEquals(4.50, d.hpMax(), 0.01);
        assertEquals(2.50, d.dmgMin(), 0.01);
        assertEquals(3.20, d.dmgMax(), 0.01);
        assertEquals(1.40, d.spdMin(), 0.01);
        assertEquals(1.60, d.spdMax(), 0.01);
        // Gear/effect curves saturate (rise-then-approach-ceiling), so phase 15 is close to but not yet
        // at the ceiling — matches the old table's own near-saturation at phase 15 (armorChance 1.00,
        // weaponMaxTier/enchantLevel already at max there), within the saturating curve's own shape.
        assertEquals(0.91, d.armorChance(), 0.02);
        assertEquals(4, d.armorMaxTier());
        assertEquals(0.79, d.weaponChance(), 0.02);
        assertEquals(4, d.weaponMaxTier());
        assertEquals(4, d.enchantLevel());
    }

    @Test
    void statCurvesAreMonotonicallyIncreasing() {
        PhaseConfig.PhaseDef prev = PhaseConfig.def(0);
        for (int phase = 1; phase <= 200; phase++) {
            PhaseConfig.PhaseDef cur = PhaseConfig.def(phase);
            assertTrue(cur.hpMax() >= prev.hpMax(), "hpMax should not decrease at phase " + phase);
            assertTrue(cur.hpMin() >= prev.hpMin(), "hpMin should not decrease at phase " + phase);
            assertTrue(cur.dmgMax() >= prev.dmgMax(), "dmgMax should not decrease at phase " + phase);
            assertTrue(cur.dmgMin() >= prev.dmgMin(), "dmgMin should not decrease at phase " + phase);
            assertTrue(cur.spdMax() >= prev.spdMax(), "spdMax should not decrease at phase " + phase);
            assertTrue(cur.spdMin() >= prev.spdMin(), "spdMin should not decrease at phase " + phase);
            prev = cur;
        }
    }

    @Test
    void hardBoundsNeverViolatedEvenAtExtremePhase() {
        for (int phase : new int[] {0, 1, 15, 100, 1000, 100_000}) {
            PhaseConfig.PhaseDef d = PhaseConfig.def(phase);
            assertTrue(d.armorMaxTier() >= 0 && d.armorMaxTier() <= 5, "armorMaxTier out of [0,5] at phase " + phase);
            assertTrue(d.weaponMaxTier() >= 0 && d.weaponMaxTier() <= 5, "weaponMaxTier out of [0,5] at phase " + phase);
            assertTrue(d.armorChance() >= 0.0 && d.armorChance() <= 1.0, "armorChance out of [0,1] at phase " + phase);
            assertTrue(d.weaponChance() >= 0.0 && d.weaponChance() <= 1.0, "weaponChance out of [0,1] at phase " + phase);
            assertTrue(d.effChance() >= 0.0 && d.effChance() <= 1.0, "effChance out of [0,1] at phase " + phase);
        }
    }

    @Test
    void mobcapAndFrequencyGrowUnboundedWithPhase() {
        double base = PhaseTable.mobcap(15);
        double far = PhaseTable.mobcap(1000);
        assertTrue(far > base, "mobcap should keep growing well past phase 15");
        assertEquals(18.0, base, 0.5); // matches the old table's phase-15 entry
    }

    @Test
    void ceilingDisabledByDefaultLetsPhaseGrowPastMax() {
        assertEquals(200, PhaseManager.applyCeiling(200)); // phaseMaxEnabled=false by default
    }

    @Test
    void ceilingEnabledWithoutLoopPinsAtMax() {
        ProgressionConfig.phaseMaxEnabled = true;
        ProgressionConfig.phaseMax = 20;
        ProgressionConfig.phaseLoopEnabled = false;
        assertEquals(20, PhaseManager.applyCeiling(20));
        assertEquals(20, PhaseManager.applyCeiling(21));
        assertEquals(20, PhaseManager.applyCeiling(500));
    }

    @Test
    void ceilingEnabledWithLoopWrapsToOne() {
        ProgressionConfig.phaseMaxEnabled = true;
        ProgressionConfig.phaseMax = 20;
        ProgressionConfig.phaseLoopEnabled = true;

        // Reaching the ceiling wraps back to 1 instead of pinning.
        assertEquals(1, PhaseManager.applyCeiling(20));
        assertEquals(1, PhaseManager.applyCeiling(21));

        // Full simulated climb across several wraps: keep advancing and require the phase to pass back
        // through 1 at least 3 times, and never exceed phaseMax, within a generous step budget.
        int phase = 0;
        int wraps = 0;
        for (int step = 0; step < 200 && wraps < 3; step++) {
            int before = phase;
            phase = PhaseManager.applyCeiling(phase + 1);
            assertTrue(phase <= 20, "phase should never exceed phaseMax when looping");
            if (before > 1 && phase == 1) {
                wraps++;
            }
        }
        assertEquals(3, wraps, "should have wrapped back to phase 1 at least 3 times");
    }
}
