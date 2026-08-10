package com.dreykaoas.lethalbreed.config.bounds.engine;

import com.dreykaoas.lethalbreed.config.BoundsRegistrar;
import com.dreykaoas.lethalbreed.config.bounds.BoundsSplitNote;

/**
 * Clamp ranges for the scheduler, LOD and expert options — the whole Perf domain.
 *
 * <p>See {@link BoundsSplitNote} for why this is its own class instead of one shared table.
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
