package com.dreykaoas.lethalbreed.config.domain;

/**
 * World rules (day/weather), spawn filtering/stripping, per-zombie deterministic variation and the random
 * beneficial spawn effect.
 */
public final class WorldSpawnConfig {
    private WorldSpawnConfig() {}

    // ---- World rules ----
    /** Force the overworld to stay daytime. */
    public static boolean forceDayTime = true;
    /** Time of day to hold (6000 = noon). */
    public static long forcedDayTime = 6000L;
    /** Keep the weather clear (no rain/thunder). */
    public static boolean clearWeather = true;

    // ---- Per-zombie variation (deterministic from UUID) ----
    /** Give each zombie slightly randomized size/strength/speed/leap (modest ranges). */
    public static boolean enableVariation = true;
    public static double varScaleMin = 0.85, varScaleMax = 1.25;   // body size
    public static double varSpeedMin = 0.9, varSpeedMax = 1.2;     // movement speed
    public static double varDamageMin = 0.85, varDamageMax = 1.3;  // attack damage
    public static double varLeapMin = 0.85, varLeapMax = 1.2;      // leap power

    // ---- Random beneficial effect on spawn (constant while alive) ----
    /** Each spawned zombie has a chance to carry one random beneficial effect for its whole life. */
    public static boolean randomEffectEnabled = true;
    /** Fraction of spawns that get an effect (0.25 = ~1 in 4). */
    public static float randomEffectChance = 0.25f;
    /** Max amplifier; the level is rolled in [0, max] so I–(max+1). 2 = up to level III. */
    public static int randomEffectMaxAmplifier = 2;
    /** Custom LEAP effect: extra horizontal leap reach per level (0.35 = +35%/level). */
    public static double leapEffectPerLevel = 0.35;

    // ---- Spawn control (Phase 1) ----
    /** Discard baby zombies on load. */
    public static boolean blockBabyZombies = true;
    /** Discard drowned on load (keeps the population to plain zombies). */
    public static boolean blockDrowned = true;
    /** Strip armor/weapons from zombies. OFF: they keep gear — a held weapon adds melee damage (vanilla)
     *  and a held tool speeds up their block breaking (see BreakManager). */
    public static boolean stripZombieEquipment = false;
    /** Force every zombie type to burn in daylight (husks too); Fire Resistance/helmet/water still protect. */
    public static boolean forceAllZombiesSunBurn = true;
}
