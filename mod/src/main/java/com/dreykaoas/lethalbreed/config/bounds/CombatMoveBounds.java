package com.dreykaoas.lethalbreed.config.bounds;

import com.dreykaoas.lethalbreed.config.BoundsRegistrar;

/**
 * Clamp ranges for the leap, water, climb and block-breaking options.
 *
 * <p>Split out of {@code ConfigBoundsTable}, which had grown to 305 append-only lines across nine unrelated
 * domains. Registration order does not matter — the table is a map keyed by lower-cased option name — but
 * grouping does: a bound belongs next to the options it governs, and {@code ConfigBoundsTest} fails the build
 * if any numeric option loses one.
 */
public final class CombatMoveBounds {
    private CombatMoveBounds() {}

    public static void register(BoundsRegistrar r) {
        r.b("leapCooldownActivations", 0, 1000);
        r.b("leapChance", 0, 1);
        r.b("leapMinRange", 0, 64);
        r.b("leapMaxRange", 0, 128);
        r.b("leapHorizontalSpeed", 0, 5);
        r.b("leapUpward", 0, 5);
        r.b("leapMaxVerticalDiff", 0, 64);       // new option (Leap)
        r.b("maxBreakHeight", 1, 16);
        r.b("waterRiseSpeed", 0, 2);
        r.b("waterDiveSpeed", 0, 2);
        r.b("waterSwimSpeed", 0, 2);
        r.b("stuckActivations", 1, 1000);
        r.b("stuckProgressEpsilon", 0, 100);
        r.b("climbJumpMaxAge", 1, 1000);
        r.b("descendThreshold", 0, 64);
        r.b("safeDropBlocks", 0, 256);
        r.b("meleeStopRange", 0, 64);
        r.b("meleeStopHeight", 0, 64);
        r.b("breakProgressPerTick", 0.001, 1.0);
        r.b("breakGraceTicks", 1, 1000);
        r.b("blockOpsPerTick", 0, 256);
        r.b("blockOpsQueueCap", 1, 20_000);
        r.b("breakMaxHardness", 0, 50);
        r.b("placedBlockLifetimeTicks", 20, 72_000);
        r.b("maxConcurrentBreaks", 1, 4096);     // new option (Breaking)
        r.b("breachRadius", 0, 64);              // focus-fire breach coordination
        r.b("maxConcurrentBreaches", 1, 64);
        r.b("breachGraceTicks", 1, 1000);
        r.b("breakConcentrationPerBreaker", 0, 10);
        r.b("breakConcentrationCap", 1, 32);
        r.b("leapLandingScanDist", 1, 64);
        r.b("leapLandingScanDepth", 1, 64);
        r.b("waterSubmergeOffset", 0, 8);
        r.b("waterArriveDistance", 0, 8);
        r.b("waterVelocityBlend", 0, 1);
        r.b("waterSurfaceJump", 0, 2);
        r.b("pillarFinishHeight", 0, 8);
        r.b("pillarFinishSpeed", 0, 5);
        r.b("pillarFinishJump", 0, 2);
        r.b("descendDirectlyBelowRadius", 0, 16);

    }
}
