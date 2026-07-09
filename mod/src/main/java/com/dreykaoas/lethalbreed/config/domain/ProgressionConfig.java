package com.dreykaoas.lethalbreed.config.domain;

/**
 * Difficulty-phase escalation, special zombie variants and the dev/headless test toggles.
 */
public final class ProgressionConfig {
    private ProgressionConfig() {}

    // ---- Difficulty phases (escalation) ----
    /** Master toggle for the 15-phase escalation (stats/gear/effects scale with the phase). */
    public static boolean phaseSystemEnabled = true;
    /** Ticks between auto phase advances (12000 = 10 min). */
    public static int phaseIntervalTicks = 12000;
    /** Random +/- jitter (ticks) applied to each interval. */
    public static int phaseJitterTicks = 1200;
    /** Drop chance per equipped gear item on death (0.02 = 2%). */
    public static double phaseGearDropChance = 0.02;

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
