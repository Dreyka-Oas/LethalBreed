package com.dreykaoas.lethalbreed.config.domain;

/**
 * World rules (day/weather), spawn filtering/stripping, per-zombie deterministic variation and the random
 * beneficial spawn effect.
 */
public final class WorldSpawnConfig {
    private WorldSpawnConfig() {}

    // ---- World rules ----
    /** Force the overworld to stay daytime. */
    public static boolean forceDayTime = false;
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

    // ---- Night-only phased spawning ----
    /** Master toggle: hostile spawns are driven by the phase (0 = classic, nothing spawns). */
    public static boolean nightSpawnEnabled = true;
    /** Only plain Zombie spawns. When ON, every other hostile (skeleton, creeper, husk, drowned, zombie
     *  villager, zombified piglin, spider, …) is cancelled at spawn, in EVERY phase. */
    public static boolean onlyPlainZombie = true;
    // Mob-cap and spawn-frequency multipliers are formula-driven (unbounded, no per-phase table any
    // more) — see PhaseTable.mobcap()/frequency() and the phaseMobcap*/phaseFrequency* knobs in
    // ProgressionConfig.
    /** SAFETY ceiling on the extra spawn passes {@code SpawnFrequencyMixin} runs per chunk per tick, not a
     *  balance knob. The frequency formula is unbounded by design, but the passes it drives are a loop on
     *  the server thread: at phase 1e6 that is ~1e6 passes per chunk per tick, i.e. a tick that never
     *  returns. At the default 30-minute phase interval the curve reaches this ceiling after ~240 h of play,
     *  so it never bites in normal progression — raise it if you actually want to go past that. */
    public static int spawnMaxExtraPasses = 512;

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
    /** Difficulty phase at (and above) which zombies become IMMUNE to daylight burning — the more the phases
     *  climb, the tougher they get. Below it they still burn (and flee to shade + sleep by day). Set very high
     *  to effectively disable the immunity. Default 5. */
    public static int sunImmunePhase = 5;
}
