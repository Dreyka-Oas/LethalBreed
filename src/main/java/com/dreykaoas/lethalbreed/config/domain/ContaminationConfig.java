package com.dreykaoas.lethalbreed.config.domain;

/**
 * Super Contamination plague: infection chance, ramping wither damage, hunger drain and the (crouch-only)
 * cure rolls.
 */
public final class ContaminationConfig {
    private ContaminationConfig() {}

    // ---- Super Contamination ----
    /** Master toggle for the contamination plague (zombie hit → may infect → ramping death → zombify). */
    public static boolean contaminationEnabled = true;
    /** Infection chance on a zombie hit, before the phase bonus. */
    public static double contamBaseChance = 0.10;
    /** Added infection chance per phase. */
    public static double contamPhaseScale = 0.02;
    /** Cap on the infection chance. */
    public static double contamMaxChance = 0.60;
    /** Wither damage at infection start (half-heart = 1.0). */
    public static double contamDamageBase = 1.0;
    /** Extra damage per contamination tick of age (ramps "infinitely" toward the cap). */
    public static double contamDamageRamp = 0.02;
    /** Damage cap per hit. */
    public static double contamDamageCap = 8.0;
    /** Ticks between contamination damage applications. */
    public static int contamDamageInterval = 20;
    /** Ticks between hunger drains (players). */
    public static int contamHungerInterval = 30;
    /** Ticks between cure rolls (only while crouched). */
    public static int contamCureCheckTicks = 40;
    /** Cure chance window per roll (percent). Tiny on purpose. */
    public static double contamCureMinPct = 5.0;
    public static double contamCureMaxPct = 8.0;
}
