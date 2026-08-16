package com.dreykaoas.lethalbreed.entity.mood;

import com.dreykaoas.lethalbreed.config.domain.ZombieMoodConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ground-gain threshold, and the wiring that was missing.
 *
 * <p>{@code fleeGroundGainThreshold} shipped as a real config option — declared, bounded, labelled in both
 * languages, visible in the config screen — while {@link FleeThreatTracker#track} compared against a literal
 * {@code 0.25}. Setting the option did nothing. Because the literal happened to equal the option's default,
 * nothing looked wrong from inside the game: every test of the default value passed either way.
 *
 * <p>That is what {@link #theThresholdComesFromConfigNotALiteral} pins. It is the only test here that would
 * have failed before the fix, and it fails for exactly one reason — a hardcoded constant.
 */
class FleeThreatTrackerTest {

    private final double savedThreshold = ZombieMoodConfig.fleeGroundGainThreshold;
    private final int savedStuck = ZombieMoodConfig.fleeStuckActivations;
    private final int savedFast = ZombieMoodConfig.fleeFastThreatGiveUp;

    @AfterEach
    void restoreConfig() {
        ZombieMoodConfig.fleeGroundGainThreshold = savedThreshold;
        ZombieMoodConfig.fleeStuckActivations = savedStuck;
        ZombieMoodConfig.fleeFastThreatGiveUp = savedFast;
    }

    @Test
    void theFirstActivationAlwaysCountsAsGainingGround() {
        // No previous distance to compare against — a fleer that just started retreating is not yet cornered.
        assertTrue(new FleeThreatTracker().track(100.0));
    }

    @Test
    void aGainBelowTheThresholdIsJitterNotProgress() {
        ZombieMoodConfig.fleeGroundGainThreshold = 0.25;
        FleeThreatTracker t = new FleeThreatTracker();
        t.track(100.0);
        assertFalse(t.track(100.20), "0.20 blocks² is under the 0.25 floor");
    }

    @Test
    void aGainAboveTheThresholdIsProgress() {
        ZombieMoodConfig.fleeGroundGainThreshold = 0.25;
        FleeThreatTracker t = new FleeThreatTracker();
        t.track(100.0);
        assertTrue(t.track(100.30), "0.30 blocks² clears the 0.25 floor");
    }

    /** The regression guard: the same 0.30 gain has to flip verdict when the option is raised past it. */
    @Test
    void theThresholdComesFromConfigNotALiteral() {
        FleeThreatTracker lenient = new FleeThreatTracker();
        ZombieMoodConfig.fleeGroundGainThreshold = 0.25;
        lenient.track(100.0);
        assertTrue(lenient.track(100.30), "gaining under a 0.25 floor");

        FleeThreatTracker strict = new FleeThreatTracker();
        ZombieMoodConfig.fleeGroundGainThreshold = 4.0;
        strict.track(100.0);
        assertFalse(strict.track(100.30), "the very same gain must not count under a 4.0 floor");
    }

    @Test
    void closingDistanceNeverCountsAsGainingGround() {
        ZombieMoodConfig.fleeGroundGainThreshold = 0.25;
        FleeThreatTracker t = new FleeThreatTracker();
        t.track(100.0);
        // The threat is closing in — distance² falls. No floor value can make that "progress".
        assertFalse(t.track(81.0));
    }

    @Test
    void aCorneredFleerGivesUpOnceTheFailuresPileUp() {
        ZombieMoodConfig.fleeGroundGainThreshold = 0.25;
        ZombieMoodConfig.fleeStuckActivations = 3;
        FleeThreatTracker t = new FleeThreatTracker();
        t.track(100.0);
        for (int i = 0; i < 2; i++) {
            t.track(100.0); // wall-blocked: identical distance, no gain
            assertFalse(t.shouldGiveUp(false), "only " + (i + 1) + " failed activation(s)");
        }
        t.track(100.0);
        assertTrue(t.shouldGiveUp(false), "three failures reaches fleeStuckActivations");
    }

    @Test
    void aThreatAtLeastAsFastEndsAHopelessFleeSooner() {
        ZombieMoodConfig.fleeGroundGainThreshold = 0.25;
        ZombieMoodConfig.fleeStuckActivations = 6;
        ZombieMoodConfig.fleeFastThreatGiveUp = 2;
        FleeThreatTracker t = new FleeThreatTracker();
        t.track(100.0);
        t.track(100.0);
        t.track(100.0);
        assertTrue(t.shouldGiveUp(true), "two failures is enough against a threat that can keep up");
        assertFalse(t.shouldGiveUp(false), "but not against a slower one, which allows six");
    }

    @Test
    void resetClearsBothTheDistanceAndTheFailureCount() {
        ZombieMoodConfig.fleeGroundGainThreshold = 0.25;
        ZombieMoodConfig.fleeStuckActivations = 2;
        FleeThreatTracker t = new FleeThreatTracker();
        t.track(100.0);
        t.track(100.0);
        t.track(100.0);
        assertTrue(t.shouldGiveUp(false));

        t.reset();
        assertFalse(t.shouldGiveUp(false), "the failure count is cleared");
        assertTrue(t.track(50.0), "and the next activation is a first one again");
    }
}
