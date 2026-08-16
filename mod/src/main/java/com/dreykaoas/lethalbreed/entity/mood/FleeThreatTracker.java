package com.dreykaoas.lethalbreed.entity.mood;

import com.dreykaoas.lethalbreed.config.domain.ZombieMoodConfig;

/**
 * Cornered-flee tracking: if the fleer can't open distance from its threat (wall-blocked / player right on top
 * of it), it gives up retreating and fights instead of standing passively. Tracks the distance-to-threat across
 * activations and the count of consecutive failures to gain ground.
 */
public final class FleeThreatTracker {
    private double lastThreatDistSq = -1.0;
    private int stuckActivations = 0;

    /** Record the current distance² to the threat; returns true if ground was gained since the last call. The
     *  gain has to clear {@link ZombieMoodConfig#fleeGroundGainThreshold} so ordinary jitter (a step sideways,
     *  a shove) doesn't read as progress and keep a cornered fleer retreating forever. */
    public boolean track(double distSq) {
        boolean gaining = lastThreatDistSq < 0.0
                || distSq > lastThreatDistSq + ZombieMoodConfig.fleeGroundGainThreshold;
        stuckActivations = gaining ? 0 : stuckActivations + 1;
        lastThreatDistSq = distSq;
        return gaining;
    }

    /** True once enough consecutive failed activations have piled up to abandon the retreat. A threat at least
     *  as fast as the fleer gives up sooner (retreating only delays death). */
    public boolean shouldGiveUp(boolean threatAtLeastAsFast) {
        int giveUp = threatAtLeastAsFast
                ? Math.min(ZombieMoodConfig.fleeFastThreatGiveUp, ZombieMoodConfig.fleeStuckActivations)
                : ZombieMoodConfig.fleeStuckActivations;
        return stuckActivations >= giveUp;
    }

    public void reset() {
        stuckActivations = 0;
        lastThreatDistSq = -1.0;
    }
}
