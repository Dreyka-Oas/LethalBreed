package com.dreykaoas.lethalbreed.special;

import com.dreykaoas.lethalbreed.config.domain.SpecialVariantConfig;
import com.dreykaoas.lethalbreed.special.runtime.BomberBlast;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Bomber's numbers, pinned. Every case here runs without a server precisely because
 * {@link BomberBlast} carries no Minecraft type — that separation is the point of the class.
 */
class BomberBlastTest {

    @BeforeEach
    void resetConfig() {
        // Config is static state shared across tests; restore the shipped defaults so a case that edits a
        // bound cannot leak into the next one.
        SpecialVariantConfig.specialBomberFuseMinTicks = 30;
        SpecialVariantConfig.specialBomberFuseMaxTicks = 120;
        SpecialVariantConfig.specialBomberPowerMin = 2.0;
        SpecialVariantConfig.specialBomberPowerMax = 5.0;
        SpecialVariantConfig.specialBomberSplatterMul = 1.5;
        SpecialVariantConfig.specialBomberInfectChance = 0.5;
        SpecialVariantConfig.specialBomberBlindThreshold = 0.75;
        SpecialVariantConfig.specialBomberEffectCountCeiling = 4;
        SpecialVariantConfig.specialBomberEffectCountDecay = 0.90;
        SpecialVariantConfig.specialBomberEffectAmpCeiling = 2;
        SpecialVariantConfig.specialBomberEffectAmpDecay = 0.92;
    }

    @Test
    void fuseSpansTheConfiguredRange() {
        assertEquals(30, BomberBlast.fuseTicksFor(0.0));
        assertEquals(120, BomberBlast.fuseTicksFor(1.0));
        assertEquals(75, BomberBlast.fuseTicksFor(0.5));
    }

    @Test
    void fuseRangeIsReorderedWhenInverted() {
        SpecialVariantConfig.specialBomberFuseMinTicks = 120;
        SpecialVariantConfig.specialBomberFuseMaxTicks = 30;
        assertEquals(30, BomberBlast.fuseTicksFor(0.0));
        assertEquals(120, BomberBlast.fuseTicksFor(1.0));
    }

    @Test
    void ratioIsZeroAtMinAndOneAtMax() {
        assertEquals(0.0, BomberBlast.ratioOf(30), 1e-9);
        assertEquals(1.0, BomberBlast.ratioOf(120), 1e-9);
        assertTrue(BomberBlast.ratioOf(60) > BomberBlast.ratioOf(45));
    }

    @Test
    void ratioIsZeroWhenTheRangeIsDegenerate() {
        SpecialVariantConfig.specialBomberFuseMinTicks = 60;
        SpecialVariantConfig.specialBomberFuseMaxTicks = 60;
        assertEquals(0.0, BomberBlast.ratioOf(60), 1e-9);
    }

    @Test
    void powerFollowsTheFuse() {
        assertEquals(2.0, BomberBlast.powerFor(0.0), 1e-9);
        assertEquals(5.0, BomberBlast.powerFor(1.0), 1e-9);
        assertEquals(3.5, BomberBlast.powerFor(0.5), 1e-9);
    }

    @Test
    void powerRangeIsReorderedWhenInverted() {
        SpecialVariantConfig.specialBomberPowerMin = 5.0;
        SpecialVariantConfig.specialBomberPowerMax = 2.0;
        assertEquals(2.0, BomberBlast.powerFor(0.0), 1e-9);
        assertEquals(5.0, BomberBlast.powerFor(1.0), 1e-9);
    }

    @Test
    void splatterReachesFurtherThanTheBlast() {
        double power = 3.0;
        assertEquals(6.0, BomberBlast.blastRadius(power), 1e-9);
        assertEquals(9.0, BomberBlast.splatterRadius(power), 1e-9);
    }

    @Test
    void intensityIsOneOnlyAtContactWithTheLongestFuse() {
        double splatR = BomberBlast.splatterRadius(BomberBlast.powerFor(1.0));
        assertEquals(1.0, BomberBlast.intensity(1.0, 0.0, splatR), 1e-9);
        // Shortest fuse at point blank keeps the proximity share only — distance never stops mattering.
        assertEquals(0.4, BomberBlast.intensity(0.0, 0.0, splatR), 1e-9);
    }

    @Test
    void intensityIsZeroAtTheSplatterEdgeAndBeyond() {
        double splatR = 15.0;
        assertEquals(0.0, BomberBlast.intensity(1.0, 15.0, splatR), 1e-9);
        assertEquals(0.0, BomberBlast.intensity(1.0, 40.0, splatR), 1e-9);
    }

    @Test
    void intensityDecreasesWithDistance() {
        double splatR = 15.0;
        double near = BomberBlast.intensity(1.0, 2.0, splatR);
        double far = BomberBlast.intensity(1.0, 10.0, splatR);
        assertTrue(near > far, "near=" + near + " far=" + far);
    }

    @Test
    void durationStillScalesWithIntensity() {
        // The effect SET became random, but distance and fuse length must still decide how long it lasts.
        assertEquals(60, BomberBlast.effectTicks(3.0, 9.0, 0.0));
        assertEquals(240, BomberBlast.effectTicks(3.0, 9.0, 1.0));
        assertTrue(BomberBlast.effectTicks(3.0, 9.0, 0.5) > BomberBlast.effectTicks(3.0, 9.0, 0.25));
    }

    @Test
    void intensityOutsideZeroToOneCannotStretchADuration() {
        assertEquals(240, BomberBlast.effectTicks(3.0, 9.0, 5.0));
        assertEquals(60, BomberBlast.effectTicks(3.0, 9.0, -5.0));
    }

    @Test
    void cocktailGrowsWithThePhaseAndStopsAtItsCeiling() {
        assertEquals(1, BomberBlast.cocktailSize(0));
        assertTrue(BomberBlast.cocktailSize(15) > BomberBlast.cocktailSize(4));
        for (int phase : new int[] {30, 100, 1_000, 1_000_000}) {
            assertTrue(BomberBlast.cocktailSize(phase)
                    <= SpecialVariantConfig.specialBomberEffectCountCeiling, "escaped at phase " + phase);
        }
    }

    @Test
    void cocktailActuallyReachesItsCeiling() {
        // Rounding rather than flooring is the whole reason: floor() would leave it one short forever.
        assertEquals(SpecialVariantConfig.specialBomberEffectCountCeiling, BomberBlast.cocktailSize(1_000));
    }

    @Test
    void aBomberAlwaysCarriesAtLeastOneEffect() {
        SpecialVariantConfig.specialBomberEffectCountCeiling = 0; // below its own configured bound
        assertTrue(BomberBlast.cocktailSize(0) >= 1);
        assertTrue(BomberBlast.cocktailSize(1_000) >= 1);
    }

    @Test
    void amplifierCeilingGrowsWithThePhaseAndIsBounded() {
        assertEquals(0, BomberBlast.cocktailMaxAmp(0));
        assertTrue(BomberBlast.cocktailMaxAmp(30) > BomberBlast.cocktailMaxAmp(4));
        for (int phase : new int[] {30, 100, 1_000, 1_000_000}) {
            assertTrue(BomberBlast.cocktailMaxAmp(phase)
                    <= SpecialVariantConfig.specialBomberEffectAmpCeiling, "escaped at phase " + phase);
        }
    }

    @Test
    void aDecayOfOneWouldNeverSaturateSoItIsClamped() {
        SpecialVariantConfig.specialBomberEffectCountDecay = 1.0;
        // Clamped to 0.999, so it still climbs — just slowly — instead of freezing at the floor forever.
        assertTrue(BomberBlast.cocktailSize(10_000) > 1);
    }

    @Test
    void splatterColourIsFullyOpaque() {
        // ENTITY_EFFECT reads this as packed ARGB and hands the alpha byte to SpellParticle.setAlpha, so an
        // alpha of 0 renders the whole cloud invisible while everything else still looks correct: the
        // particles spawn, travel and expire, and nothing logs a warning. This shipped once already.
        int alpha = (BomberBlast.SPLATTER_COLOR_ARGB >>> 24) & 0xFF;
        assertEquals(0xFF, alpha);
    }

    @Test
    void puddleLingersLongerForALongerFuse() {
        assertTrue(BomberBlast.puddleDurationTicks(1.0) > BomberBlast.puddleDurationTicks(0.0));
        assertTrue(BomberBlast.puddleDurationTicks(0.0) > 0);
    }

    @Test
    void puddlePoolsTighterThanTheRingThatFedIt() {
        assertTrue(BomberBlast.puddleRadius(10.0) < 10.0);
        assertEquals(0.0, BomberBlast.puddleRadius(0.0), 1e-9);
    }

    @Test
    void puddleShrinksToNothingExactlyAsItExpires() {
        assertEquals(8.0, BomberBlast.puddleRadiusAt(8.0, 0, 100), 1e-9);
        assertEquals(4.0, BomberBlast.puddleRadiusAt(8.0, 50, 100), 1e-9);
        // At and beyond expiry the hazard is gone, so nobody is clipped by a puddle they can't see.
        assertEquals(0.0, BomberBlast.puddleRadiusAt(8.0, 100, 100), 1e-9);
        assertEquals(0.0, BomberBlast.puddleRadiusAt(8.0, 300, 100), 1e-9);
    }

    @Test
    void puddleDurationOfZeroCannotDivideByZero() {
        assertEquals(0.0, BomberBlast.puddleRadiusAt(8.0, 0, 0), 1e-9);
    }

    @Test
    void puddleDosesSofterThanTheBurstButFallsOffTheSameWay() {
        // Residue is a hazard to linger in, not a second explosion.
        assertTrue(BomberBlast.puddleIntensity(1.0, 0.0, 5.0) < BomberBlast.intensity(1.0, 0.0, 5.0));
        // Same curve underneath: nothing at the edge, more at the centre.
        assertEquals(0.0, BomberBlast.puddleIntensity(1.0, 5.0, 5.0), 1e-9);
        assertTrue(BomberBlast.puddleIntensity(1.0, 1.0, 5.0) > BomberBlast.puddleIntensity(1.0, 4.0, 5.0));
    }

    @Test
    void blindnessOnlyBecomesEligibleAboveItsThreshold() {
        assertFalse(BomberBlast.blindnessEligible(0.74));
        assertTrue(BomberBlast.blindnessEligible(0.76));
    }

    @Test
    void blindnessIsDisabledWhenTheThresholdIsOne() {
        // 1.0 is a legal bound and no intensity can exceed it, so this must switch Blindness off entirely.
        SpecialVariantConfig.specialBomberBlindThreshold = 1.0;
        assertFalse(BomberBlast.blindnessEligible(1.0));
        assertFalse(BomberBlast.blindnessEligible(0.99));
    }
}
