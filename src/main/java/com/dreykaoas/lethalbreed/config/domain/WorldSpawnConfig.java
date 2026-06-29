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
    public static double varHealthMin = 0.9, varHealthMax = 1.2;   // max health

    // ---- Random beneficial effect on spawn (constant while alive) ----
    /** Master gate for spawn effects: when OFF, NEITHER the legacy flat roll NOR the phase effects apply. */
    public static boolean randomEffectEnabled = true;
    /** LEGACY ONLY: fraction of spawns that get an effect when the phase system is OFF (0.25 = ~1 in 4).
     *  Ignored by the phase path, which uses each phase's own effChance. */
    public static float randomEffectChance = 0.25f;
    /** Global hard amplifier ceiling — applies to BOTH the legacy roll AND the phase effects (capped via
     *  Math.min against the phase's effMaxAmp). Default 3 = the table's current max, so it is a no-op cap
     *  by default; lower it to nerf effect strength everywhere. Level rolled in [0, max]. */
    public static int randomEffectMaxAmplifier = 3;
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
    /** Fire ticks applied per sun-burn trigger (160 = 8s, like vanilla). */
    public static int sunBurnDurationTicks = 160;
}
