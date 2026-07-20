package com.dreykaoas.lethalbreed.config;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Pure data: the sane numeric range registered for each clamped config field, keyed by lower-cased field name
 * (matching {@link ConfigSchema#find}'s case-insensitivity). No logic lives here — {@link ConfigBounds#clamp}
 * looks a field up via {@link #get} and applies the range. Split out so the (large, append-only) table grows
 * without bloating the clamp logic.
 */
final class ConfigBoundsTable {
    private ConfigBoundsTable() {}

    record Range(double min, double max) {}

    private static final Map<String, Range> BOUNDS = new HashMap<>();

    private static void b(String name, double min, double max) {
        BOUNDS.put(name.toLowerCase(Locale.ROOT), new Range(min, max));
    }

    /** Registered range for a field, or {@code null} when the field is unbounded (passes through unchanged). */
    static Range get(String name) {
        return BOUNDS.get(name.toLowerCase(Locale.ROOT));
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
        b("navYThreshold", 0, 64);
        b("flowBreakCost", 0, 100_000);
        b("flowBuildCost", 0, 100_000);
        b("flowOrthoCost", 1, 1000);
        b("flowDiagonalCost", 1, 1000);
        b("climbThreshold", 0, 64);
        b("climbHorizRadius", 0, 64);
        b("maxClimbHeight", 1, 256);
        b("climbGiveUpCooldown", 0, 1000);
        b("pillarMaxHeight", 1, 256);
        b("pillarJumpPower", 0, 2);

        // ---- TargetingConfig (Targeting / Sound) ----
        b("targetDetectRadius", 0, 128);
        b("targetMemoryTicks", 0, 72_000);
        b("targetSwitchMargin", 1, 8);
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
        b("breachRadius", 0, 64);              // focus-fire breach coordination
        b("maxConcurrentBreaches", 1, 64);
        b("breachGraceTicks", 1, 1000);
        b("breakConcentrationPerBreaker", 0, 10);
        b("breakConcentrationCap", 1, 32);
        b("leapLandingScanDist", 1, 64);
        b("leapLandingScanDepth", 1, 64);
        b("waterSubmergeOffset", 0, 8);
        b("waterArriveDistance", 0, 8);
        b("waterVelocityBlend", 0, 1);
        b("waterSurfaceJump", 0, 2);
        b("pillarFinishHeight", 0, 8);
        b("pillarFinishSpeed", 0, 5);
        b("pillarFinishJump", 0, 2);
        b("descendDirectlyBelowRadius", 0, 16);

        // ---- ProgressionConfig (Phases / Specials / Dev) ----
        b("phaseIntervalTicks", 1, 1_000_000);
        b("phaseJitterTicks", 0, 1_000_000);
        b("phaseHpMaxGrowth", 0, 10);
        b("phaseHpMinGrowth", 0, 10);
        b("phaseHpExponent", 0.5, 3.0);
        b("phaseDmgMaxGrowth", 0, 10);
        b("phaseDmgMinGrowth", 0, 10);
        b("phaseDmgExponent", 0.5, 3.0);
        b("phaseSpdMaxGrowth", 0, 10);
        b("phaseSpdMinGrowth", 0, 10);
        b("phaseSpdExponent", 0.5, 3.0);
        b("phaseEffChanceDecay", 0.5, 0.999);
        b("phaseEffCountDecay", 0.5, 0.999);
        b("phaseEffCountCeiling", 1, 9);
        b("phaseEffAmpDecay", 0.5, 0.999);
        b("phaseEffAmpCeiling", 0, 10);
        b("phaseMobcapGrowth", 0, 100);
        b("phaseMobcapExponent", 0.5, 3.0);
        b("phaseFrequencyGrowth", 0, 100);
        b("phaseFrequencyExponent", 0.5, 3.0);
        b("phaseMax", 1, 1_000_000);
        b("specialBaseChance", 0, 1);
        b("specialPhaseScale", 0, 1);
        b("specialMaxChance", 0, 1);
        b("specialActionInterval", 1, 1000);
        // Per-type unlock phase (0 = always available) + selection weight (0 = never picked).
        b("specialSprinteurPhase", 0, 1_000_000);
        b("specialSprinteurWeight", 0, 1_000_000);
        b("specialBondisseurPhase", 0, 1_000_000);
        b("specialBondisseurWeight", 0, 1_000_000);
        b("specialBombeurPhase", 0, 1_000_000);
        b("specialBombeurWeight", 0, 1_000_000);
        b("specialHurleurPhase", 0, 1_000_000);
        b("specialHurleurWeight", 0, 1_000_000);
        b("specialSoigneurPhase", 0, 1_000_000);
        b("specialSoigneurWeight", 0, 1_000_000);
        b("specialJuggernautPhase", 0, 1_000_000);
        b("specialJuggernautWeight", 0, 1_000_000);
        b("specialNecromancienPhase", 0, 1_000_000);
        b("specialNecromancienWeight", 0, 1_000_000);
        b("specialSplitterPhase", 0, 1_000_000);
        b("specialSplitterWeight", 0, 1_000_000);
        // Per-type behaviour magnitudes.
        b("specialBombeurPower", 0, 100);
        b("specialBombeurArmRange", 0, 64);
        b("specialBombeurFusePerTick", 0.001, 1.0);
        b("specialHurleurRadius", 0, 128);
        b("specialSoigneurRadius", 0, 128);
        b("specialSoigneurRegenTicks", 1, 72_000);
        b("specialSoigneurRegenAmp", 0, 9);
        b("specialNecromancienMinChildren", 0, 64);
        b("specialNecromancienMaxChildren", 0, 64);
        b("specialNecromancienDensityCap", 0, 10_000);
        b("specialNecromancienDensityRadius", 0, 128);
        b("specialNecromancienSpread", 0, 16);
        b("specialSplitterChildren", 0, 64);
        b("specialSplitterChildScale", 0.05, 10);
        b("specialSplitterSpread", 0, 16);
        b("specialSprinteurSpeedAmp", 0, 9);
        b("specialSprinteurSpeedMul", 0, 10);
        b("specialBondisseurLeapAmp", 0, 9);
        b("specialJuggernautScale", 0.05, 10);
        b("specialJuggernautHealthMul", 0.05, 100);
        b("specialJuggernautResistanceAmp", 0, 9);
        b("devSpawnRadius", 1, 256);

        // ---- ContaminationConfig (Contamination) ----
        b("contamBaseChance", 0, 1);
        b("contamPhaseScale", 0, 1);
        b("contamMaxChance", 0, 1);
        b("contamDamageMin", 0, 1000);
        b("contamDamageMax", 0, 1000);
        b("contamIntervalMinSec", 0, 3600);
        b("contamIntervalMaxSec", 0, 3600);
        b("contamCureCheckTicks", 1, 72_000);
        b("contamCureMinPct", 0, 100);
        b("contamCureMaxPct", 0, 100);
        b("contamSymptomMinDays", 0, 365);
        b("contamSymptomMaxDays", 0, 365);
        b("contamSymptomMinPct", 0, 100);
        b("contamSymptomMaxPct", 0, 100);
        b("contamLatentSlowAmount", 0, 1);
        b("contamLatentSlowTicks", 1, 72_000);
        b("contamReanimateMinHeight", 0, 16);
        b("contamReanimateMaxWidth", 0, 16);
        b("contamReanimateAspect", 0, 100);
        b("contamEpisodeCap", 0, 1);
        b("contamFoodExhaustionMult", 0, 100);

        // ---- ZombieMoodConfig (Mood) ----
        b("fleeHealthFraction", 0, 1);
        b("regainHealthFraction", 0, 1);
        b("regenAmount", 0, 1024);
        b("regenIntervalTicks", 1, 72_000);
        b("fleeSpeed", 0, 10);
        b("fleeThreatRadius", 0, 128);
        b("fleeDistance", 0, 128);
        b("fleeGroundGainThreshold", 0, 100);
        b("fleeStuckActivations", 0, 1024);
        b("corneredFightTicks", 0, 72_000);
        b("fleeFastThreatGiveUp", 1, 1024);
        b("shelterSearchRadius", 1, 64);
        b("shelterSpeed", 0, 10);
        b("distressDistance", 0, 128);
        b("distressRallyRadius", 0, 128);
        b("celebrateRadius", 0, 128);
        b("celebrateTicks", 0, 72_000);
        b("screamVolume", 0, 64);
        b("victoryPitch", 0.5, 2.0);
        b("distressPitch", 0.5, 2.0);

        // ---- ExpertConfig (Expert) — floors kept strictly > 0, divisor >= 1, to preserve the guards ----
        b("expertStepDeadzone", 0, 4);
        b("expertBreakHeightEpsilon", 0, 1);
        b("expertHeadingEpsilon", 0, 1);
        b("expertPillarHeadingEpsilon", 0, 1);
        b("expertPillarCeilingOffset", 0, 8);
        b("expertPillarSupportHeight", 0, 8);
        b("expertAttributeFloor", 0, 10);
        b("expertMobcapChunkDivisor", 1, 1_000_000);
        b("expertContamIntensityFloor", 0.000_001, 1000);
        b("expertContamTimeScaleFloor", 0.000_001, 1000);
    }
}
