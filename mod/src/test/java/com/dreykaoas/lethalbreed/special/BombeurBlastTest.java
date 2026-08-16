package com.dreykaoas.lethalbreed.special;

import com.dreykaoas.lethalbreed.config.domain.SpecialVariantConfig;
import com.dreykaoas.lethalbreed.special.runtime.BombeurBlast;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        SpecialVariantConfig.specialBombeurEffectCountCeiling = 4;
        SpecialVariantConfig.specialBombeurEffectCountDecay = 0.90;
        SpecialVariantConfig.specialBombeurEffectAmpCeiling = 2;
        SpecialVariantConfig.specialBombeurEffectAmpDecay = 0.92;
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
    void durationStillScalesWithIntensity() {
        // The effect SET became random, but distance and fuse length must still decide how long it lasts.
        assertEquals(60, BombeurBlast.effectTicks(3.0, 9.0, 0.0));
        assertEquals(240, BombeurBlast.effectTicks(3.0, 9.0, 1.0));
        assertTrue(BombeurBlast.effectTicks(3.0, 9.0, 0.5) > BombeurBlast.effectTicks(3.0, 9.0, 0.25));
    }

    @Test
    void intensityOutsideZeroToOneCannotStretchADuration() {
        assertEquals(240, BombeurBlast.effectTicks(3.0, 9.0, 5.0));
        assertEquals(60, BombeurBlast.effectTicks(3.0, 9.0, -5.0));
    }

    @Test
    void cocktailGrowsWithThePhaseAndStopsAtItsCeiling() {
        assertEquals(1, BombeurBlast.cocktailSize(0));
        assertTrue(BombeurBlast.cocktailSize(15) > BombeurBlast.cocktailSize(4));
        for (int phase : new int[] {30, 100, 1_000, 1_000_000}) {
            assertTrue(BombeurBlast.cocktailSize(phase)
                    <= SpecialVariantConfig.specialBombeurEffectCountCeiling, "escaped at phase " + phase);
        }
    }

    @Test
    void cocktailActuallyReachesItsCeiling() {
        // Rounding rather than flooring is the whole reason: floor() would leave it one short forever.
        assertEquals(SpecialVariantConfig.specialBombeurEffectCountCeiling, BombeurBlast.cocktailSize(1_000));
    }

    @Test
    void aBombeurAlwaysCarriesAtLeastOneEffect() {
        SpecialVariantConfig.specialBombeurEffectCountCeiling = 0; // below its own configured bound
        assertTrue(BombeurBlast.cocktailSize(0) >= 1);
        assertTrue(BombeurBlast.cocktailSize(1_000) >= 1);
    }

    @Test
    void amplifierCeilingGrowsWithThePhaseAndIsBounded() {
        assertEquals(0, BombeurBlast.cocktailMaxAmp(0));
        assertTrue(BombeurBlast.cocktailMaxAmp(30) > BombeurBlast.cocktailMaxAmp(4));
        for (int phase : new int[] {30, 100, 1_000, 1_000_000}) {
            assertTrue(BombeurBlast.cocktailMaxAmp(phase)
                    <= SpecialVariantConfig.specialBombeurEffectAmpCeiling, "escaped at phase " + phase);
        }
    }

    @Test
    void aDecayOfOneWouldNeverSaturateSoItIsClamped() {
        SpecialVariantConfig.specialBombeurEffectCountDecay = 1.0;
        // Clamped to 0.999, so it still climbs — just slowly — instead of freezing at the floor forever.
        assertTrue(BombeurBlast.cocktailSize(10_000) > 1);
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
    void puddleLingersLongerForALongerFuse() {
        assertTrue(BombeurBlast.puddleDurationTicks(1.0) > BombeurBlast.puddleDurationTicks(0.0));
        assertTrue(BombeurBlast.puddleDurationTicks(0.0) > 0);
    }

    @Test
    void puddlePoolsTighterThanTheRingThatFedIt() {
        assertTrue(BombeurBlast.puddleRadius(10.0) < 10.0);
        assertEquals(0.0, BombeurBlast.puddleRadius(0.0), 1e-9);
    }

    @Test
    void puddleShrinksToNothingExactlyAsItExpires() {
        assertEquals(8.0, BombeurBlast.puddleRadiusAt(8.0, 0, 100), 1e-9);
        assertEquals(4.0, BombeurBlast.puddleRadiusAt(8.0, 50, 100), 1e-9);
        // At and beyond expiry the hazard is gone, so nobody is clipped by a puddle they can't see.
        assertEquals(0.0, BombeurBlast.puddleRadiusAt(8.0, 100, 100), 1e-9);
        assertEquals(0.0, BombeurBlast.puddleRadiusAt(8.0, 300, 100), 1e-9);
    }

    @Test
    void puddleDurationOfZeroCannotDivideByZero() {
        assertEquals(0.0, BombeurBlast.puddleRadiusAt(8.0, 0, 0), 1e-9);
    }

    @Test
    void puddleDosesSofterThanTheBurstButFallsOffTheSameWay() {
        // Residue is a hazard to linger in, not a second explosion.
        assertTrue(BombeurBlast.puddleIntensity(1.0, 0.0, 5.0) < BombeurBlast.intensity(1.0, 0.0, 5.0));
        // Same curve underneath: nothing at the edge, more at the centre.
        assertEquals(0.0, BombeurBlast.puddleIntensity(1.0, 5.0, 5.0), 1e-9);
        assertTrue(BombeurBlast.puddleIntensity(1.0, 1.0, 5.0) > BombeurBlast.puddleIntensity(1.0, 4.0, 5.0));
    }

    @Test
    void blindnessOnlyBecomesEligibleAboveItsThreshold() {
        assertFalse(BombeurBlast.blindnessEligible(0.74));
        assertTrue(BombeurBlast.blindnessEligible(0.76));
    }

    @Test
    void blindnessIsDisabledWhenTheThresholdIsOne() {
        // 1.0 is a legal bound and no intensity can exceed it, so this must switch Blindness off entirely.
        SpecialVariantConfig.specialBombeurBlindThreshold = 1.0;
        assertFalse(BombeurBlast.blindnessEligible(1.0));
        assertFalse(BombeurBlast.blindnessEligible(0.99));
    }
}
