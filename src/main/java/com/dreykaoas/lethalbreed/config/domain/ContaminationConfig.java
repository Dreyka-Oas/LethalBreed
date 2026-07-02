package com.dreykaoas.lethalbreed.config.domain;

/**
 * Super Contamination plague: infection chance, ramping wither damage, hunger drain and the (crouch-only)
 * cure rolls.
 */
public final class ContaminationConfig {
    private ContaminationConfig() {}

    // ---- Super Contamination ----
    /** Master toggle for the contamination plague (zombie hit → may infect → ramping DoT → death). */
    public static boolean contaminationEnabled = true;
    /** Infection chance on a zombie hit at phase 0 (lowest difficulty). Scales up to {@link #contamMaxChance}. */
    public static double contamBaseChance = 0.02;
    /** Added infection chance per phase. 15 phases (0..14) → default spans 2%..45% by phase 14. */
    public static double contamPhaseScale = 0.0307;
    /** Cap on the infection chance (highest difficulty). */
    public static double contamMaxChance = 0.45;

    // ---- Latent phase (invisible): infected but symptoms not yet triggered ----
    // Nothing is shown (no HUD tint, no icon, no particles). No plague damage yet. The only tell is a brief,
    // particleless slow at the moment of infection. Symptoms surface later via the random trigger below.
    /** Min in-game DAYS (24000-tick cycles) between symptom-trigger rolls while latent. */
    public static double contamSymptomMinDays = 5.0;
    /** Max in-game DAYS between symptom-trigger rolls while latent. */
    public static double contamSymptomMaxDays = 10.0;
    /** Min chance (percent) per roll that latent symptoms become visible. */
    public static double contamSymptomMinPct = 2.0;
    /** Max chance (percent) per roll that latent symptoms become visible. */
    public static double contamSymptomMaxPct = 10.0;
    /** Latent slow strength at infection (fraction of base movement speed removed). Particleless. Default 15%. */
    public static double contamLatentSlowAmount = 0.15;
    /** Latent slow duration in ticks (very short). Default 40 = 2 s. */
    public static int contamLatentSlowTicks = 40;
    /** Min damage per plague pulse, applied to BOTH health and (players) food. Half-heart = 1.0. Default 0.1. */
    public static double contamDamageMin = 0.1;
    /** Max damage per plague pulse (health + food). Each pulse rolls uniformly in [min, max]. Default 0.5. */
    public static double contamDamageMax = 0.5;
    /** Min real seconds between plague pulses. Each pulse reschedules the next in [min, max] s. Default 5. */
    public static double contamIntervalMinSec = 5.0;
    /** Max real seconds between plague pulses. Default 10. */
    public static double contamIntervalMaxSec = 10.0;
    /** On death of a contaminated HUMANOID victim (player/villager/piglin...), reanimate it as a zombie. */
    public static boolean contamReanimateHumanoids = true;
    /** Ticks between cure rolls (only while crouched). */
    public static int contamCureCheckTicks = 40;
    /** Cure chance window per roll (percent). Tiny on purpose. */
    public static double contamCureMinPct = 5.0;
    public static double contamCureMaxPct = 8.0;

    /** Dev time-compression for plague timers: every pulse/symptom interval is divided by this factor, so a
     *  developer can watch the (normally days-long) progression fast. Default 1.0 = real timing. Only ever
     *  changed by the dev-only {@code /lethaldev timescale} command; a shipped config keeps it at 1.0. */
    public static double contamDevTimeScale = 1.0;
}
