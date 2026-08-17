package com.dreykaoas.lethalbreed.pack;

import com.dreykaoas.lethalbreed.config.domain.PackConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackMergeRuleTest {

    private final double savedRadius = PackConfig.packMergeRadius;
    private final double savedDot = PackConfig.packMergeHeadingDot;
    private final int savedMinSize = PackConfig.packMinSize;
    private final int savedGrace = PackConfig.packDissolveGraceTicks;

    @AfterEach
    void restoreConfig() {
        PackConfig.packMergeRadius = savedRadius;
        PackConfig.packMergeHeadingDot = savedDot;
        PackConfig.packMinSize = savedMinSize;
        PackConfig.packDissolveGraceTicks = savedGrace;
    }

    /** Two packs at a given separation, both heading due north unless told otherwise. */
    private static boolean mergeAt(double separation, double bHeadingX, double bHeadingZ) {
        return PackMergeRule.shouldMerge(0, 0, 0, 1, 6,
                separation, 0, bHeadingX, bHeadingZ, 6);
    }

    @Test
    void closePacksHeadingTheSameWayMerge() {
        PackConfig.packMergeRadius = 32.0;
        assertTrue(mergeAt(10.0, 0, 1));
    }

    @Test
    void distantPacksDoNotMerge() {
        PackConfig.packMergeRadius = 32.0;
        assertFalse(mergeAt(40.0, 0, 1));
    }

    @Test
    void theRadiusIsInclusiveAtItsEdge() {
        PackConfig.packMergeRadius = 32.0;
        assertTrue(mergeAt(32.0, 0, 1));
    }

    /** The rule that stops the whole world population collapsing into one mass: packs crossing in opposite
     *  directions pass through each other instead of fusing. */
    @Test
    void packsCrossingInOppositeDirectionsPassThroughEachOther() {
        PackConfig.packMergeRadius = 32.0;
        PackConfig.packMergeHeadingDot = 0.5;
        assertFalse(mergeAt(4.0, 0, -1));
    }

    @Test
    void perpendicularHeadingsDoNotMergeAtTheDefaultThreshold() {
        PackConfig.packMergeRadius = 32.0;
        PackConfig.packMergeHeadingDot = 0.5;
        // dot = 0 for headings 90° apart, below the 0.5 threshold.
        assertFalse(mergeAt(4.0, 1, 0));
    }

    @Test
    void aThresholdOfMinusOneMakesHeadingIrrelevant() {
        PackConfig.packMergeRadius = 32.0;
        PackConfig.packMergeHeadingDot = -1.0;
        assertTrue(mergeAt(4.0, 0, -1));
    }

    // ---- Which one absorbs which ----

    @Test
    void theBiggerPackAbsorbs() {
        assertEquals(1L, PackMergeRule.survivor(1L, 9, 2L, 3));
        assertEquals(2L, PackMergeRule.survivor(1L, 3, 2L, 9));
    }

    @Test
    void atEqualSizeTheLowestIdSurvivesSoTheOutcomeIsDeterministic() {
        assertEquals(1L, PackMergeRule.survivor(1L, 6, 2L, 6));
        assertEquals(1L, PackMergeRule.survivor(2L, 6, 1L, 6));
    }

    // ---- Dissolution ----

    @Test
    void aHealthyPackIsNeverDissolved() {
        PackConfig.packMinSize = 2;
        PackConfig.packDissolveGraceTicks = 200;
        assertFalse(PackMergeRule.shouldDissolve(5, 10_000));
    }

    @Test
    void anUndersizedPackSurvivesTheGracePeriod() {
        PackConfig.packMinSize = 2;
        PackConfig.packDissolveGraceTicks = 200;
        assertFalse(PackMergeRule.shouldDissolve(1, 199));
        assertTrue(PackMergeRule.shouldDissolve(1, 200));
    }

    @Test
    void anEmptyPackGoesImmediatelyWhateverTheGrace() {
        PackConfig.packMinSize = 2;
        PackConfig.packDissolveGraceTicks = 72_000;
        // A pack object without a single member has nothing left to wait for: keeping it alive
        // for an hour of play would only advance it and re-materialise nothing.
        assertTrue(PackMergeRule.shouldDissolve(0, 0));
    }
}
