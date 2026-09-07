package oas.dreyka.lethalbreed.config.bounds;

import oas.dreyka.lethalbreed.config.BoundsRegistrar;

/**
 * Clamp ranges for the targeting and sound options (Targeting / Sound).
 *
 * <p>{@code ConfigBoundsTest} fails the build if a numeric option loses its bound.
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
        r.b("targetMaxPreyHeight", 0, 64);
    }
}
