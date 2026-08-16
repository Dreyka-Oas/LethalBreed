package com.dreykaoas.lethalbreed.config.domain;

/**
 * The infinite difficulty-phase escalation: the advance timer, the stat-scaling curves, the beneficial-effect
 * curves, the mobcap/frequency curves, the colour tiers and the optional ceiling/loop.
 *
 * <p>Special-variant tuning lives in {@link SpecialVariantConfig}; this class used to hold both, plus the
 * headless-test toggles that now live in the {@code dev} source set ({@code DevTestConfig}) and never ship.
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

    // ---- Phase mobcap/frequency formula: value(phase) = growth * phase^exponent, bent onto a ceiling ----
    /** Growth rate for the night-spawn mob-cap multiplier. Bounded by {@link #phaseMobcapCeiling}. */
    public static double phaseMobcapGrowth = 0.9153;
    /** Exponent for the mob-cap curve. */
    public static double phaseMobcapExponent = 1.1;
    /** Growth rate for the night-spawn frequency multiplier. Bounded by {@link #phaseFrequencyCeiling}. */
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
     *  {@link #phaseMaxEnabled} is false. Only the auto-advance timer respects this — the dev-only
     *  {@code /lethalphase} command can still force any phase manually. */
    public static boolean phaseLoopEnabled = false;

    // ---- Ceilings on the phase curves: value approaches the ceiling from phase 15 up, never reaching it ----
    // Appended at the end of the class on purpose: ConfigWriter keeps schema order within a category, so
    // inserting these next to the curves they bound would rewrite every existing player's config file.
    /** Ceiling on the damage multiplier curve. With the shipped base of 3.0 and the widest variation roll,
     *  6.0 is what puts an un-buffed zombie exactly at the 18.9 raw damage a netherite player survives 3 of. */
    public static double phaseDmgCeiling = 6.0;
    /** Ceiling on the max-HP multiplier curve. 8.0 leaves a top-phase zombie around 25 netherite sword hits. */
    public static double phaseHpCeiling = 8.0;
    /** Ceiling on the movement-speed multiplier curve. */
    public static double phaseSpdCeiling = 2.2;
    /** Ceiling on the night-spawn mob-cap multiplier. This one is a performance bound as much as a balance
     *  one: nothing in this mod despawns, and the raw curve reaches 144x vanilla by phase 100. */
    public static double phaseMobcapCeiling = 30.0;
    /** Ceiling on the night-spawn frequency multiplier. */
    public static double phaseFrequencyCeiling = 24.0;

    // ---- Hard caps on the FINAL attribute value, enforced after every effect and special has been stamped.
    // The curve ceilings above bound the multiplier only; a rolled Strength/Health Boost is an ADD_VALUE that
    // lands in the base that multiplier scales, so it re-crosses them. These are the actual guarantee. ----
    /** Hardest a zombie may ever hit, raw. 18.9 = three hits through un-enchanted full netherite. */
    public static double phaseDamageCap = 18.9;
    /** Most health a zombie may ever have. */
    public static double phaseHealthCap = 200.0;
    /** Fastest a zombie may ever move. 0.45 preserves today's phase-15 feel (0.414) while stopping the
     *  runaway that reaches 0.66 with a rolled Speed III — a sprinting player moves at roughly 0.28. */
    public static double phaseSpeedCap = 0.45;
}
