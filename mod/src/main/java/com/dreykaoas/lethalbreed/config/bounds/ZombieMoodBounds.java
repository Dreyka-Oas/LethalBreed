package com.dreykaoas.lethalbreed.config.bounds;

import com.dreykaoas.lethalbreed.config.BoundsRegistrar;

/**
 * Clamp ranges for the mood options.
 *
 * <p>See {@link BoundsSplitNote} for why this is its own class instead of one shared table.
 */
public final class ZombieMoodBounds {
    private ZombieMoodBounds() {}

    public static void register(BoundsRegistrar r) {
        r.b("fleeHealthFraction", 0, 1);
        r.b("regainHealthFraction", 0, 1);
        r.b("regenAmount", 0, 1024);
        r.b("regenIntervalTicks", 1, 72_000);
        r.b("fleeSpeed", 0, 10);
        r.b("fleeThreatRadius", 0, 128);
        r.b("fleeDistance", 0, 128);
        r.b("fleeGroundGainThreshold", 0, 100);
        r.b("fleeStuckActivations", 0, 1024);
        r.b("corneredFightTicks", 0, 72_000);
        r.b("fleeFastThreatGiveUp", 1, 1024);
        r.b("shelterSearchRadius", 1, 64);
        r.b("shelterSpeed", 0, 10);
        r.b("shelterRetryTicks", 0, 12000);
        r.b("distressDistance", 0, 128);
        r.b("distressRallyRadius", 0, 128);
        r.b("celebrateRadius", 0, 128);
        r.b("celebrateTicks", 0, 72_000);
        r.b("screamVolume", 0, 64);
        r.b("victoryPitch", 0.5, 2.0);
        r.b("distressPitch", 0.5, 2.0);
        r.b("daySleepWakeDelayTicks", 0, 6000);
        r.b("daySleepAlertTicks", 0, 72_000);
        r.b("dayAwakePhaseStart", 0, 1_000_000);
        r.b("dayAwakePhaseSlope", 0, 1);

    }
}
