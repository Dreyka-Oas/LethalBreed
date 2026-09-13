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
        // Never below 0.05: TargetOrder.WET_PENALTY only separates wet from dry while no dry band can reach
        // it, and the band is the detect radius squared divided by this squared.
        r.b("targetTieBandBlocks", 0.05, 64);
        r.b("soundStepRadiusMax", 1, 64);
        r.b("soundScanIntervalTicks", 1, 200);
        r.b("soundLoudMultiplier", 1, 16);
        r.b("soundMoveThreshold", 0, 10);
        r.b("soundArriveDistance", 0, 64);
        r.b("targetMaxPreyHeight", 0, 64);
    }
}
