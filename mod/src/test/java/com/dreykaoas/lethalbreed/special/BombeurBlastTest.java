package com.dreykaoas.lethalbreed.special;

import com.dreykaoas.lethalbreed.config.domain.SpecialVariantConfig;
import com.dreykaoas.lethalbreed.special.runtime.BombeurBlast;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Bombeur's numbers, pinned. Every case here runs without a server precisely because
 * {@link BombeurBlast} carries no Minecraft type — that separation is the point of the class.
 */
class BombeurBlastTest {

    @BeforeEach
    void resetConfig() {
        // Config is static state shared across tests; restore the shipped defaults so a case that edits a
        // bound cannot leak into the next one.
        SpecialVariantConfig.specialBombeurFuseMinTicks = 30;
        SpecialVariantConfig.specialBombeurFuseMaxTicks = 120;
        SpecialVariantConfig.specialBombeurPowerMin = 2.0;
        SpecialVariantConfig.specialBombeurPowerMax = 5.0;
        SpecialVariantConfig.specialBombeurSplatterMul = 1.5;
        SpecialVariantConfig.specialBombeurInfectChance = 0.5;
        SpecialVariantConfig.specialBombeurBlindThreshold = 0.75;
    }

    @Test
    void fuseSpansTheConfiguredRange() {
        assertEquals(30, BombeurBlast.fuseTicksFor(0.0));
        assertEquals(120, BombeurBlast.fuseTicksFor(1.0));
        assertEquals(75, BombeurBlast.fuseTicksFor(0.5));
    }

    @Test
    void fuseRangeIsReorderedWhenInverted() {
        SpecialVariantConfig.specialBombeurFuseMinTicks = 120;
        SpecialVariantConfig.specialBombeurFuseMaxTicks = 30;
        assertEquals(30, BombeurBlast.fuseTicksFor(0.0));
        assertEquals(120, BombeurBlast.fuseTicksFor(1.0));
    }

    @Test
    void ratioIsZeroAtMinAndOneAtMax() {
        assertEquals(0.0, BombeurBlast.ratioOf(30), 1e-9);
        assertEquals(1.0, BombeurBlast.ratioOf(120), 1e-9);
        assertTrue(BombeurBlast.ratioOf(60) > BombeurBlast.ratioOf(45));
    }

    @Test
    void ratioIsZeroWhenTheRangeIsDegenerate() {
        SpecialVariantConfig.specialBombeurFuseMinTicks = 60;
        SpecialVariantConfig.specialBombeurFuseMaxTicks = 60;
        assertEquals(0.0, BombeurBlast.ratioOf(60), 1e-9);
    }

    @Test
    void powerFollowsTheFuse() {
        assertEquals(2.0, BombeurBlast.powerFor(0.0), 1e-9);
        assertEquals(5.0, BombeurBlast.powerFor(1.0), 1e-9);
        assertEquals(3.5, BombeurBlast.powerFor(0.5), 1e-9);
    }

    @Test
    void powerRangeIsReorderedWhenInverted() {
        SpecialVariantConfig.specialBombeurPowerMin = 5.0;
        SpecialVariantConfig.specialBombeurPowerMax = 2.0;
        assertEquals(2.0, BombeurBlast.powerFor(0.0), 1e-9);
        assertEquals(5.0, BombeurBlast.powerFor(1.0), 1e-9);
    }

    @Test
    void splatterReachesFurtherThanTheBlast() {
        double power = 3.0;
        assertEquals(6.0, BombeurBlast.blastRadius(power), 1e-9);
        assertEquals(9.0, BombeurBlast.splatterRadius(power), 1e-9);
    }

    @Test
    void intensityIsOneOnlyAtContactWithTheLongestFuse() {
        double splatR = BombeurBlast.splatterRadius(BombeurBlast.powerFor(1.0));
        assertEquals(1.0, BombeurBlast.intensity(1.0, 0.0, splatR), 1e-9);
        // Shortest fuse at point blank keeps the proximity share only — distance never stops mattering.
        assertEquals(0.4, BombeurBlast.intensity(0.0, 0.0, splatR), 1e-9);
    }

    @Test
    void intensityIsZeroAtTheSplatterEdgeAndBeyond() {
        double splatR = 15.0;
        assertEquals(0.0, BombeurBlast.intensity(1.0, 15.0, splatR), 1e-9);
        assertEquals(0.0, BombeurBlast.intensity(1.0, 40.0, splatR), 1e-9);
    }

    @Test
    void intensityDecreasesWithDistance() {
        double splatR = 15.0;
        double near = BombeurBlast.intensity(1.0, 2.0, splatR);
        double far = BombeurBlast.intensity(1.0, 10.0, splatR);
        assertTrue(near > far, "near=" + near + " far=" + far);
    }

    @Test
    void effectShapesMatchTheSpecTable() {
        // Spec §6, intensity 1.0: Nausea 15 s, Poison 12 s A1, Slowness 12 s A2, Blindness 5 s, 50 %.
        assertEquals(300, BombeurBlast.nauseaTicks(1.0));
        assertEquals(240, BombeurBlast.poisonTicks(1.0));
        assertEquals(1, BombeurBlast.poisonAmp(1.0));
        assertEquals(240, BombeurBlast.slowTicks(1.0));
        assertEquals(2, BombeurBlast.slowAmp(1.0));
        assertEquals(100, BombeurBlast.blindTicks(1.0));
        assertEquals(0.5, BombeurBlast.infectChance(1.0), 1e-9);
    }

    @Test
    void poisonAmplifierStepsUpAtItsThreshold() {
        assertEquals(0, BombeurBlast.poisonAmp(0.59));
        assertEquals(1, BombeurBlast.poisonAmp(0.61));
    }

    @Test
    void blindnessOnlyAppearsAboveTheThreshold() {
        assertEquals(0, BombeurBlast.blindTicks(0.74));
        assertTrue(BombeurBlast.blindTicks(0.76) > 0);
    }

    @Test
    void splatterColourIsFullyOpaque() {
        // ENTITY_EFFECT reads this as packed ARGB and hands the alpha byte to SpellParticle.setAlpha, so an
        // alpha of 0 renders the whole cloud invisible while everything else still looks correct: the
        // particles spawn, travel and expire, and nothing logs a warning. This shipped once already.
        int alpha = (BombeurBlast.SPLATTER_COLOR_ARGB >>> 24) & 0xFF;
        assertEquals(0xFF, alpha);
    }

    @Test
    void blindnessIsDisabledWhenTheThresholdIsOne() {
        // 1.0 is a legal bound, and it is also the divisor that would blow up — one guard covers both.
        SpecialVariantConfig.specialBombeurBlindThreshold = 1.0;
        assertEquals(0, BombeurBlast.blindTicks(1.0));
        assertEquals(0, BombeurBlast.blindTicks(0.99));
    }
}
