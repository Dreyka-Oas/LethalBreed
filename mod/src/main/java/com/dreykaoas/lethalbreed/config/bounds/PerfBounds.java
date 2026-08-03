package com.dreykaoas.lethalbreed.config.bounds;

import com.dreykaoas.lethalbreed.config.BoundsRegistrar;

/**
 * Clamp ranges for the scheduler, LOD and expert options — the whole Perf domain.
 *
 * <p>Split out of {@code ConfigBoundsTable}, which had grown to 305 append-only lines across nine unrelated
 * domains. Registration order does not matter — the table is a map keyed by lower-cased option name — but
 * grouping does: a bound belongs next to the options it governs, and {@code ConfigBoundsTest} fails the build
 * if any numeric option loses one.
 */
public final class PerfBounds {
    private PerfBounds() {}

    public static void register(BoundsRegistrar r) {
        r.b("tickBuckets", 1, 1000);
        r.b("autoScaleBucketLoad", 1, 100_000);
        r.b("aiTickBudget", 0, 1_000_000);
        r.b("spatialCellSize", 1, 64);
        r.b("spatialVerticalLimit", 0, 512);
        r.b("lodHigh", 0, 1024);
        r.b("lodMedium", 0, 1024);
        r.b("lodLow", 0, 1024);
        r.b("lodHysteresis", 0, 256);
        r.b("lodHardFreezeRadius", 0, 4096);
        r.b("frozenReclassifyDivisor", 1, 1000);
        r.b("lodMediumTickDivisor", 1, 1000);
        r.b("lodLowTickDivisor", 1, 1000);
        r.b("navReissueInterval", 1, 1000);
        r.b("lodMediumNavMultiplier", 1, 1000);
        r.b("lodLowNavMultiplier", 1, 1000);
        r.b("msptThrottleThreshold", 1, 1000);
        r.b("debugLogInterval", 0, 1_000_000);


        r.b("expertStepDeadzone", 0, 4);
        r.b("expertBreakHeightEpsilon", 0, 1);
        r.b("expertHeadingEpsilon", 0, 1);
        r.b("expertPillarHeadingEpsilon", 0, 1);
        r.b("expertPillarCeilingOffset", 0, 8);
        r.b("expertPillarSupportHeight", 0, 8);
        r.b("expertAttributeFloor", 0, 10);
        r.b("expertMobcapChunkDivisor", 1, 1_000_000);
        r.b("expertContamIntensityFloor", 0.000_001, 1000);
        r.b("expertContamTimeScaleFloor", 0.000_001, 1000);
    }
}
