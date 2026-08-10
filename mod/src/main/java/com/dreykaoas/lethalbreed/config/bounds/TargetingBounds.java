package com.dreykaoas.lethalbreed.config.bounds;

import com.dreykaoas.lethalbreed.config.BoundsRegistrar;

/**
 * Clamp ranges for the targeting and sound options (Targeting / Sound).
 *
 * <p>See {@link BoundsSplitNote} for why this is its own class instead of one shared table.
 */
public final class TargetingBounds {
    private TargetingBounds() {}

    public static void register(BoundsRegistrar r) {
        r.b("targetDetectRadius", 0, 128);
        r.b("targetMemoryTicks", 0, 72_000);
        r.b("targetSwitchMargin", 1, 8);
        r.b("soundBaseRadius", 0, 128);
        r.b("soundLoudMultiplier", 1, 16);
        r.b("soundMoveThreshold", 0, 10);
        r.b("soundArriveDistance", 0, 64);

    }
}
