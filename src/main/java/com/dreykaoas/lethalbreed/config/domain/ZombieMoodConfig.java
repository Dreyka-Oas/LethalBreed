package com.dreykaoas.lethalbreed.config.domain;

/**
 * Zombie "mood" behaviours layered on top of the hunt: a victory celebration when a zombie clears an area,
 * a low-health flee (with a distress scream that rallies idle kin), and a slow self-heal while fleeing or
 * celebrating. Only real zombies have these — driven per-activation by {@code entity.ZombieMood}.
 */
public final class ZombieMoodConfig {
    private ZombieMoodConfig() {}

    /** Master toggle for all three mood behaviours (celebrate / flee / regen). */
    public static boolean moodEnabled = true;

    // ---- Flee / regen health hysteresis (fraction of max health) ----
    /** Drop BELOW this fraction of max health (with a threat around) → start fleeing. Default 1/3. */
    public static double fleeHealthFraction = 0.3333;
    /** Climb back to/above this fraction → stop fleeing AND stop regenerating. Default 1/2. Must be &gt;
     *  {@link #fleeHealthFraction} to form a stable hysteresis band (no flee/fight flip-flop between them). */
    public static double regainHealthFraction = 0.5;

    // ---- Self-heal (only while fleeing or celebrating, below the regain fraction) ----
    /** Health points restored per heal tick. Default 0.5 (a quarter-heart). */
    public static double regenAmount = 0.5;
    /** Game-ticks between heal ticks. Default 100 = every 5 s. */
    public static int regenIntervalTicks = 100;

    // ---- Flee movement ----
    /** Navigation speed multiplier while fleeing (a touch faster than the chase pace). */
    public static double fleeSpeed = 1.3;
    /** A damager beyond this many blocks no longer counts as a threat (area considered clear). */
    public static double fleeThreatRadius = 24.0;
    /** How far past itself (blocks) the fleer aims its retreat path, directly away from the threat. */
    public static double fleeDistance = 8.0;
    /** Activations of failing to open distance from the threat (cornered/wall-blocked) before the fleer gives
     *  up retreating and turns to fight. Stops a cornered wounded zombie from standing passively. Default 6. */
    public static int fleeStuckActivations = 6;
    /** After giving up a blocked flee, fight for at least this many ticks before flee can re-trigger — keeps
     *  a cornered zombie attacking instead of flip-flopping flee/fight against the wall. Default 60 = 3 s. */
    public static int corneredFightTicks = 60;

    // ---- Distress scream (rallies idle zombies to the fleer) ----
    /** Once the fleer is at least this far from its threat, it screams for help (once per flee episode). */
    public static double distressDistance = 12.0;
    /** Hearing radius of the distress scream: idle (unfocused) zombies within it path to the fleer. */
    public static double distressRallyRadius = 32.0;

    // ---- Victory celebration ----
    /** After a direct kill, celebrate only if NO other valid prey sits within this radius (area cleared). */
    public static double celebrateRadius = 16.0;
    /** Duration of the arms-raised victory pose + how long the mood latch holds. 40 = 2 s. */
    public static int celebrateTicks = 40;

    // ---- Scream sound (reuses vanilla zombie groan, amplified) ----
    /** Volume of both screams. &gt;1 also widens the audible range (vanilla: range = volume × 16 blocks). */
    public static float screamVolume = 4.0f;
    /** Pitch of the victory scream (high = triumphant). */
    public static float victoryPitch = 1.4f;
    /** Pitch of the distress scream (low = anguished). */
    public static float distressPitch = 0.6f;
}
