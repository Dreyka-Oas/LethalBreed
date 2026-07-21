package com.dreykaoas.lethalbreed.config.domain;

/**
 * Difficulty-phase escalation, special zombie variants and the dev/headless test toggles.
 */
public final class ProgressionConfig {
    private ProgressionConfig() {}

    // ---- Difficulty phases (escalation) ----
    /** Master toggle for the infinite phase escalation (stats/gear/effects scale with the phase). */
    public static boolean phaseSystemEnabled = true;
    /** Ticks between auto phase advances (36000 = 30 min). */
    public static int phaseIntervalTicks = 36000;
    /** Random +/- jitter (ticks) applied to each interval (3600 = ±3 min). */
    public static int phaseJitterTicks = 3600;

    // ---- Phase stat-scaling formula: value(phase) = 1.0 + growth * phase^exponent ----
    /** Growth rate for the max-HP multiplier curve. */
    public static double phaseHpMaxGrowth = 0.04595;
    /** Growth rate for the min-HP multiplier curve. */
    public static double phaseHpMinGrowth = 0.02626;
    /** Exponent shared by the min/max HP curves. */
    public static double phaseHpExponent = 1.6;
    /** Growth rate for the max-damage multiplier curve. */
    public static double phaseDmgMaxGrowth = 0.04965;
    /** Growth rate for the min-damage multiplier curve. */
    public static double phaseDmgMinGrowth = 0.03385;
    /** Exponent shared by the min/max damage curves. */
    public static double phaseDmgExponent = 1.4;
    /** Growth rate for the max-speed multiplier curve. */
    public static double phaseSpdMaxGrowth = 0.04000;
    /** Growth rate for the min-speed multiplier curve. */
    public static double phaseSpdMinGrowth = 0.02667;
    /** Exponent shared by the min/max speed curves. */
    public static double phaseSpdExponent = 1.0;

    // ---- Phase effect formula: value(phase) = ceiling - ceiling * decay^phase ----
    /** Decay rate for the beneficial-effect roll chance. */
    public static double phaseEffChanceDecay = 0.85;
    /** Decay rate for the beneficial-effect count progression. */
    public static double phaseEffCountDecay = 0.75;
    /** Ceiling (max) number of beneficial effects rolled per zombie. */
    public static int phaseEffCountCeiling = 5;
    /** Decay rate for the beneficial-effect amplifier progression. */
    public static double phaseEffAmpDecay = 0.75;
    /** Ceiling (max) beneficial-effect amplifier from the phase curve (still hard-capped globally by
     *  {@link WorldSpawnConfig#randomEffectMaxAmplifier}). */
    public static int phaseEffAmpCeiling = 3;

    // ---- Phase mobcap/frequency formula: value(phase) = growth * phase^exponent, unbounded ----
    /** Growth rate for the night-spawn mob-cap multiplier. No ceiling — grows forever with phase. */
    public static double phaseMobcapGrowth = 0.9153;
    /** Exponent for the mob-cap curve. */
    public static double phaseMobcapExponent = 1.1;
    /** Growth rate for the night-spawn frequency multiplier. No ceiling — grows forever with phase. */
    public static double phaseFrequencyGrowth = 1.0667;
    /** Exponent for the frequency curve. */
    public static double phaseFrequencyExponent = 1.0;

    // ---- Phase display: color tier thresholds (phase >= threshold[i] -> palette[i % palette.length]) ----
    /** Phase thresholds where the broadcast/command color changes. Add/remove entries to add/remove
     *  tiers; the color palette itself cycles through a fixed built-in list in {@code PhaseManager}. */
    public static double[] phaseColorThresholds = {0, 5, 10, 15, 20, 30, 50, 75, 100};

    // ---- Optional phase ceiling + loop (both OFF by default = today's behavior: unbounded) ----
    /** Enable a hard ceiling on the phase number. OFF by default (phase stays infinite/unbounded). */
    public static boolean phaseMaxEnabled = false;
    /** The ceiling itself (only used when {@link #phaseMaxEnabled} is true). */
    public static int phaseMax = 50;
    /** Once {@link #phaseMax} is reached (and {@link #phaseMaxEnabled} is true): true = loop back to
     *  phase 1 and climb again; false = stay pinned at {@link #phaseMax}. No effect when
     *  {@link #phaseMaxEnabled} is false. Only the auto-advance timer respects this — {@code /lethalphase}
     *  can still force any phase manually. */
    public static boolean phaseLoopEnabled = false;

    // ---- Special zombie variants ----
    /** Master toggle for special zombie types (sprinter, spitter, necromancer, …). */
    public static boolean specialEnabled = true;
    /** Base chance a spawn is special, before the phase bonus. */
    public static double specialBaseChance = 0.05;
    /** Added chance per phase (0.015 = +1.5%/phase). */
    public static double specialPhaseScale = 0.015;
    /** Hard cap on the special chance. */
    public static double specialMaxChance = 0.35;
    /** Show the special type as a floating name over the zombie (off — no nametags cluttering the screen). */
    public static boolean specialShowName = false;
    /** Activations between an ACTIVE special's repeated actions (bucketed, so ~5 ticks each). */
    public static int specialActionInterval = 10;

    // Per special type: from which phase it can appear (…Phase) and its relative selection weight
    // (…Weight, higher = more frequent; 0 = never picked). Defaults match the built-in table, so leaving
    // them untouched changes nothing. Read live by SpecialType, so edits apply without a restart.
    public static int specialSprinteurPhase = 2;
    public static int specialSprinteurWeight = 10;
    public static int specialBondisseurPhase = 3;
    public static int specialBondisseurWeight = 9;
    public static int specialBombeurPhase = 4;
    public static int specialBombeurWeight = 7;
    public static int specialHurleurPhase = 5;
    public static int specialHurleurWeight = 7;
    public static int specialSoigneurPhase = 6;
    public static int specialSoigneurWeight = 6;
    public static int specialJuggernautPhase = 6;
    public static int specialJuggernautWeight = 6;
    public static int specialNecromancienPhase = 9;
    public static int specialNecromancienWeight = 4;
    public static int specialSplitterPhase = 11;
    public static int specialSplitterWeight = 4;

    // Per special type: behaviour magnitudes. Defaults reproduce the built-in behaviour exactly, so leaving
    // them untouched changes nothing. All read live at spawn/activation, so edits apply without a restart.
    // -- Bombeur (ACTIVE): explosion + belly fuse.
    /** Explosion power of a Bombeur detonation (vanilla TNT ≈ 4.0). */
    public static double specialBombeurPower = 3.0;
    /** Distance (blocks) to the target at which a Bombeur arms and commits to detonating. */
    public static double specialBombeurArmRange = 3.0;
    /** Belly charge added per activation; the Bombeur detonates once it reaches 1.0 (0.06 ≈ 17 activations). */
    public static double specialBombeurFusePerTick = 0.06;
    // -- Hurleur (ACTIVE): rally.
    /** Radius (blocks) within which a Hurleur hands its target to nearby idle zombies. */
    public static double specialHurleurRadius = 24.0;
    // -- Soigneur (ACTIVE): heal aura.
    /** Radius (blocks) of the Soigneur's regeneration aura. */
    public static double specialSoigneurRadius = 8.0;
    /** Duration (ticks) of the Regeneration granted by a Soigneur. */
    public static int specialSoigneurRegenTicks = 100;
    /** Amplifier of the Regeneration granted by a Soigneur (0 = Regeneration I). */
    public static int specialSoigneurRegenAmp = 0;
    // -- Nécromancien (ACTIVE): summon.
    /** Minimum children a Nécromancien summons per activation. */
    public static int specialNecromancienMinChildren = 1;
    /** Maximum children a Nécromancien summons per activation. */
    public static int specialNecromancienMaxChildren = 2;
    /** Skip the summon if more than this many zombies are already within the density radius. */
    public static int specialNecromancienDensityCap = 40;
    /** Radius (blocks) of the local-density check that gates a Nécromancien summon. */
    public static double specialNecromancienDensityRadius = 12.0;
    /** Scatter (blocks) of summoned children around the Nécromancien. */
    public static int specialNecromancienSpread = 2;
    // -- Splitter (DEATH): split on death.
    /** Number of small children a Splitter spawns when it dies. */
    public static int specialSplitterChildren = 2;
    /** Size multiplier of each Splitter child (0.6 = 60% size). */
    public static double specialSplitterChildScale = 0.6;
    /** Scatter (blocks) of the children a Splitter spawns on death. */
    public static int specialSplitterSpread = 1;
    // -- Sprinteur (PASSIVE): speed.
    /** Speed effect amplifier on a Sprinteur (1 = Speed II). */
    public static int specialSprinteurSpeedAmp = 1;
    /** Movement-speed attribute multiplier on a Sprinteur. */
    public static double specialSprinteurSpeedMul = 1.35;
    // -- Bondisseur (PASSIVE): leap.
    /** LEAP effect amplifier on a Bondisseur (2 = LEAP III). */
    public static int specialBondisseurLeapAmp = 2;
    // -- Juggernaut (PASSIVE): tank.
    /** Size multiplier on a Juggernaut. */
    public static double specialJuggernautScale = 1.4;
    /** Max-health multiplier on a Juggernaut. */
    public static double specialJuggernautHealthMul = 2.0;
    /** Resistance effect amplifier on a Juggernaut (1 = Resistance II). */
    public static int specialJuggernautResistanceAmp = 1;

    /** Dev: headless special-zombie verification arena on server start (logs PASS/FAIL). Off for shipping. */
    public static boolean devSpecialTest = false;
    /** Dev: headless mechanics arena (sun-burn / phase gear / contamination). Off for shipping. */
    public static boolean devMechTest = false;
    /** Dev: headless Compute-backend self-test on server start — solves a synthetic field on CPU and GPU and
     *  logs CPU sanity + GPU/CPU parity + dynamic-pool + routing checks. No world mutation. Off for shipping. */
    public static boolean devComputeTest = false;

    // ---- Dev climb test (headless) ----
    /** Build a wall + villager-on-top + zombies arena on server start, for autonomous climb testing. */
    public static boolean devClimbTest = false;
    /** Log each targeting zombie's approach/climb state ([ClimbDbg] lines). Auto-enabled by the climb test. */
    public static boolean debugClimb = false;

    /** Radius (blocks) around the player used by the /lethalspawn dev command. */
    public static int devSpawnRadius = 16;
}
