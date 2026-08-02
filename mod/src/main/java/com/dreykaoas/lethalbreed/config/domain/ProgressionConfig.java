package com.dreykaoas.lethalbreed.config.domain;

/**
 * The infinite difficulty-phase escalation: the advance timer, the stat-scaling curves, the beneficial-effect
 * curves, the mobcap/frequency curves, the colour tiers and the optional ceiling/loop.
 *
 * <p>Special-variant tuning lives in {@link SpecialVariantConfig} and the headless-test toggles in
 * {@link DevTestConfig}; this class used to hold all three.
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
}
