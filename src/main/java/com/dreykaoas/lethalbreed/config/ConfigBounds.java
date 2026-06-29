package com.dreykaoas.lethalbreed.config;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Sane numeric bounds for config options, applied centrally in {@link ConfigAccess#apply}. The config layer
 * is reflection-based and intentionally schema-free, but a raw {@code f.set} from the GUI, a command or a
 * hand-edited JSON would otherwise accept pathological values (negative grid sizes → {@code
 * NegativeArraySizeException}, {@code flowCpuThreads=99999} → {@code ForkJoinPool} overflow, a chance &gt; 1
 * silently breaking a roll, {@code NaN}/{@code Infinity} poisoning an attribute modifier, …).
 *
 * <p>Clamping here covers EVERY entry point (GUI packet, command, JSON load) in one place, keeps the
 * reflective {@link ConfigSchema} untouched, and is a no-op for every in-range value — so default configs
 * and sane edits behave exactly as before. Booleans and unlisted fields pass through unchanged.
 *
 * <p>Bounds are keyed by the lower-cased field name (matching {@link ConfigSchema#find}'s case-insensitivity).
 */
public final class ConfigBounds {
    private ConfigBounds() {}

    private record Range(double min, double max) {}

    private static final Map<String, Range> BOUNDS = new HashMap<>();

    private static void b(String name, double min, double max) {
        BOUNDS.put(name.toLowerCase(Locale.ROOT), new Range(min, max));
    }

    static {
        // ---- SchedulerConfig (Perf) ----
        b("tickBuckets", 1, 1000);
        b("autoScaleBucketLoad", 1, 100_000);
        b("aiTickBudget", 0, 1_000_000);
        b("spatialCellSize", 1, 64);
        b("spatialVerticalLimit", 0, 512);
        b("lodHigh", 0, 1024);
        b("lodMedium", 0, 1024);
        b("lodLow", 0, 1024);
        b("lodHysteresis", 0, 256);
        b("lodHardFreezeRadius", 0, 4096);
        b("frozenReclassifyDivisor", 1, 1000);
        b("lodMediumTickDivisor", 1, 1000);
        b("lodLowTickDivisor", 1, 1000);
        b("navReissueInterval", 1, 1000);
        b("lodMediumNavMultiplier", 1, 1000);
        b("lodLowNavMultiplier", 1, 1000);
        b("msptThrottleThreshold", 1, 1000);
        b("debugLogInterval", 0, 1_000_000);

        // ---- FlowConfig (Compute / Pathing / Climb) ----
        b("flowCpuThreads", 0, 256);
        b("gpuWorkgroupSize", 0, 1024);
        b("gpuMinCells", 0, 10_000_000);
        b("gpuDeviceIndex", -1, 64);
        b("flowRecomputeInterval", 1, 6000);
        b("flowResampleOnMoveDist", 0, 1024);
        b("flowMargin", 0, 256);
        b("flowMaxGrid", 1, 512);
        b("flowVerticalTolerance", 0, 64);
        b("flowWaypointStep", 1, 64);
        b("navSpeed", 0, 10);
        b("flowBreakCost", 0, 100_000);
        b("flowBuildCost", 0, 100_000);
        b("flowOrthoCost", 1, 1000);
        b("flowDiagonalCost", 1, 1000);
        b("climbThreshold", 0, 64);
        b("climbHorizRadius", 0, 64);
        b("wallClimbSpeed", 0, 2);
        b("maxClimbHeight", 1, 256);
        b("climbGiveUpCooldown", 0, 1000);
        b("pillarMaxHeight", 1, 256);
        b("pillarJumpPower", 0, 2);

        // ---- TargetingConfig (Targeting / Sound) ----
        b("targetDetectRadius", 0, 128);
        b("targetMemoryTicks", 0, 72_000);
        b("soundBaseRadius", 0, 128);
        b("soundLoudMultiplier", 1, 16);
        b("soundMoveThreshold", 0, 10);
        b("soundArriveDistance", 0, 64);

        // ---- WorldSpawnConfig (World / Variation / Effects / Spawn) ----
        b("forcedDayTime", 0, 24_000);
        b("varScaleMin", 0.05, 10);
        b("varScaleMax", 0.05, 10);
        b("varSpeedMin", 0.05, 10);
        b("varSpeedMax", 0.05, 10);
        b("varDamageMin", 0, 100);
        b("varDamageMax", 0, 100);
        b("varLeapMin", 0, 10);
        b("varLeapMax", 0, 10);
        b("varHealthMin", 0.05, 10);   // new option (Variation)
        b("varHealthMax", 0.05, 10);
        b("randomEffectChance", 0, 1);
        b("randomEffectMaxAmplifier", 0, 9);
        b("leapEffectPerLevel", 0, 5);
        b("sunBurnDurationTicks", 20, 6000);   // new option (Spawn)

        // ---- CombatMoveConfig (Leap / Water / Climb / Breaking) ----
        b("leapCooldownActivations", 0, 1000);
        b("leapChance", 0, 1);
        b("leapMinRange", 0, 64);
        b("leapMaxRange", 0, 128);
        b("leapHorizontalSpeed", 0, 5);
        b("leapUpward", 0, 5);
        b("leapMaxVerticalDiff", 0, 64);       // new option (Leap)
        b("maxBreakHeight", 1, 16);
        b("waterRiseSpeed", 0, 2);
        b("waterDiveSpeed", 0, 2);
        b("waterSwimSpeed", 0, 2);
        b("stuckActivations", 1, 1000);
        b("stuckProgressEpsilon", 0, 100);
        b("climbJumpMaxAge", 1, 1000);
        b("descendThreshold", 0, 64);
        b("safeDropBlocks", 0, 256);
        b("meleeStopRange", 0, 64);
        b("meleeStopHeight", 0, 64);
        b("breakProgressPerTick", 0.001, 1.0);
        b("breakGraceTicks", 1, 1000);
        b("blockOpsPerTick", 0, 256);
        b("blockOpsQueueCap", 1, 20_000);
        b("breakMaxHardness", 0, 50);
        b("placedBlockLifetimeTicks", 20, 72_000);
        b("maxConcurrentBreaks", 1, 4096);     // new option (Breaking)

        // ---- ProgressionConfig (Phases / Specials / Dev) ----
        b("phaseIntervalTicks", 1, 1_000_000);
        b("phaseJitterTicks", 0, 1_000_000);
        b("phaseGearDropChance", 0, 1);
        b("specialBaseChance", 0, 1);
        b("specialPhaseScale", 0, 1);
        b("specialMaxChance", 0, 1);
        b("specialActionInterval", 1, 1000);
        b("devSpawnRadius", 1, 256);

        // ---- ContaminationConfig (Contamination) ----
        b("contamBaseChance", 0, 1);
        b("contamPhaseScale", 0, 1);
        b("contamMaxChance", 0, 1);
        b("contamDamageBase", 0, 1000);
        b("contamDamageRamp", 0, 1000);
        b("contamDamageCap", 0, 1000);
        b("contamDamageInterval", 1, 72_000);
        b("contamHungerInterval", 1, 72_000);
        b("contamCureCheckTicks", 1, 72_000);
        b("contamCureMinPct", 0, 100);
        b("contamCureMaxPct", 0, 100);
    }

    /**
     * Clamp a freshly parsed value to its registered bounds. Returns the value unchanged when the field has
     * no bounds or is a boolean. Non-finite doubles/floats ({@code NaN}/{@code Infinity}) are pulled to the
     * lower bound (a safe, in-range value) rather than allowed to poison downstream math.
     */
    public static Object clamp(String name, Object value) {
        Range r = BOUNDS.get(name.toLowerCase(Locale.ROOT));
        if (r == null) {
            return value;
        }
        if (value instanceof Integer i) {
            return (int) Math.max(r.min, Math.min((double) i, r.max));
        }
        if (value instanceof Long l) {
            return (long) Math.max(r.min, Math.min((double) l, r.max));
        }
        if (value instanceof Float f) {
            if (!Float.isFinite(f)) {
                return (float) r.min;
            }
            return (float) Math.max(r.min, Math.min((double) f, r.max));
        }
        if (value instanceof Double d) {
            if (!Double.isFinite(d)) {
                return r.min;
            }
            return Math.max(r.min, Math.min(d, r.max));
        }
        return value;
    }
}
