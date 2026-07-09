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
    // ---- Contamination evolution (levels 1..5) ----
    // While symptomatic, every few in-game days a roll may bump the plague to the next level (up to the cap). A
    // higher level scales EVERYTHING up: episode strength/duration/frequency, plague-pulse damage, and the
    // client screen overlay (greener + dark blotches). Each victim also gets its OWN random intensity jitter per
    // level, so two people at the same level suffer differently.
    /** Highest plague level. Level 1 = the base symptomatic stage; 5 = worst. */
    public static int contamMaxLevel = 5;
    /** Min/max in-game DAYS between level-up rolls while symptomatic. */
    public static double contamEvolveMinDays = 1.0;
    public static double contamEvolveMaxDays = 2.0;
    /** Min/max chance (percent) per roll to gain the next level. */
    public static double contamEvolveMinPct = 15.0;
    public static double contamEvolveMaxPct = 35.0;
    /** Per-level intensity step: effective multiplier = 1 + (level-1) × step × jitter, where jitter is a random
     *  [jitterMin, jitterMax] rolled per victim per level (so each person's curve differs). Default step 0.6. */
    public static double contamLevelStep = 0.6;
    public static double contamLevelJitterMin = 0.7;
    public static double contamLevelJitterMax = 1.3;

    // ---- Symptomatic episodic afflictions (only while symptomatic) ----
    // Three independent, particleless attribute episodes flare at random. Each has its own on/off timer, so they
    // may overlap or fire alone. All applied as transient attribute modifiers (no effect icon).

    /** Movement-slow episode: fraction of base movement speed removed while active. Default 30%. */
    public static double contamSlowAmount = 0.15;
    /** Min/max seconds a slow episode lasts. Default 5–10 s. */
    public static double contamSlowDurMinSec = 5.0;
    public static double contamSlowDurMaxSec = 10.0;
    /** Min/max seconds between slow episodes (measured from the end of one to the start of the next). 60–180 s. */
    public static double contamSlowGapMinSec = 60.0;
    public static double contamSlowGapMaxSec = 180.0;

    /** No-jump episode: while active, JUMP_STRENGTH is forced to 0 (cannot jump). Min/max duration. Default 4–8 s. */
    public static double contamNoJumpDurMinSec = 4.0;
    public static double contamNoJumpDurMaxSec = 8.0;
    /** Min/max seconds between no-jump episodes. Default 90–240 s. */
    public static double contamNoJumpGapMinSec = 90.0;
    public static double contamNoJumpGapMaxSec = 240.0;

    /** Weak-strike episode: fraction of base attack damage removed while active. Default 40%. */
    public static double contamWeakAmount = 0.40;
    /** Min/max seconds a weak-strike episode lasts. Default 5–10 s. */
    public static double contamWeakDurMinSec = 5.0;
    public static double contamWeakDurMaxSec = 10.0;
    /** Min/max seconds between weak-strike episodes. Default 75–210 s. */
    public static double contamWeakGapMinSec = 75.0;
    public static double contamWeakGapMaxSec = 210.0;

    /** Zombie-vision hallucination episode (client-only visual): while active, the victim sees every OTHER player
     *  as a zombie. Min/max seconds it lasts. Default 6–14 s. */
    public static double contamHallucDurMinSec = 6.0;
    public static double contamHallucDurMaxSec = 14.0;
    /** Min/max seconds between hallucination episodes. Default 80–220 s. */
    public static double contamHallucGapMinSec = 80.0;
    public static double contamHallucGapMaxSec = 220.0;

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
