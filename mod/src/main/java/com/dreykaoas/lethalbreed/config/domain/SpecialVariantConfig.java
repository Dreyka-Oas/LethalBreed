package com.dreykaoas.lethalbreed.config.domain;

/**
 * The eight special zombie variants: roll weights and the per-variant stat multipliers.
 *
 * <p>Split out of {@code ProgressionConfig}, which held three unrelated domains. Field NAMES are
 * unchanged and the holders stay adjacent in {@code ConfigSchema.HOLDERS} in their original order, so
 * the on-disk JSON, {@code ConfigBoundsTable}, {@code ConfigCategory} and every translation key are
 * unaffected — see {@code ConfigSchemaOrderTest}.
 */
public final class SpecialVariantConfig {
    private SpecialVariantConfig() {}

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
    public static int specialSprinterPhase = 2;
    public static int specialSprinterWeight = 10;
    public static int specialLeaperPhase = 3;
    public static int specialLeaperWeight = 9;
    public static int specialBomberPhase = 4;
    public static int specialBomberWeight = 7;
    public static int specialScreamerPhase = 5;
    public static int specialScreamerWeight = 7;
    public static int specialHealerPhase = 6;
    public static int specialHealerWeight = 6;
    public static int specialJuggernautPhase = 6;
    public static int specialJuggernautWeight = 6;
    public static int specialNecromancerPhase = 9;
    public static int specialNecromancerWeight = 4;
    public static int specialSplitterPhase = 11;
    public static int specialSplitterWeight = 4;

    // Per special type: behaviour magnitudes. Defaults reproduce the built-in behaviour exactly, so leaving
    // them untouched changes nothing. All read live at spawn/activation, so edits apply without a restart.
    // -- Bombeur (ACTIVE): timed fuse + explosion + infectious splatter.
    /** Distance (blocks) to the target at which a Bombeur arms and commits to detonating. */
    public static double specialBomberArmRange = 3.0;
    /** Shortest fuse, in GAME TICKS (not activations): the floor of the random roll made when it arms. */
    public static int specialBomberFuseMinTicks = 30;
    /** Longest fuse, in GAME TICKS. A long fuse gives the victim time, and detonates harder for it. */
    public static int specialBomberFuseMaxTicks = 120;
    /** Explosion power of the shortest fuse (vanilla TNT ≈ 4.0). */
    public static double specialBomberPowerMin = 2.0;
    /** Explosion power of the longest fuse — the payoff for having swelled the whole time. */
    public static double specialBomberPowerMax = 5.0;
    /** Splatter radius as a multiple of the blast radius. Above 1.0, backing out of lethal range still
     *  leaves you inside the gore — which is the whole point of the ring. */
    public static double specialBomberSplatterMul = 1.5;
    /** Contamination chance at intensity 1.0; scales down linearly with intensity. */
    public static double specialBomberInfectChance = 0.5;
    /** Splatter intensity from which Blindness is applied. 1.0 disables Blindness entirely. */
    public static double specialBomberBlindThreshold = 0.75;
    /** Most distinct effects one Bombeur's gore cocktail can carry. */
    public static int specialBomberEffectCountCeiling = 4;
    /** How fast the cocktail grows toward its ceiling with the phase. Lower = reaches the ceiling sooner. */
    public static double specialBomberEffectCountDecay = 0.90;
    /** Highest amplifier the cocktail may roll (2 = level III). Each effect draws uniformly in [0, this]. */
    public static int specialBomberEffectAmpCeiling = 2;
    /** How fast the amplifier ceiling grows toward its maximum with the phase. */
    public static double specialBomberEffectAmpDecay = 0.92;
    /** Whether the lingering gore puddle can also transmit the contamination, once per victim per puddle. */
    public static boolean specialBomberPuddleInfect = true;
    // -- Hurleur (ACTIVE): rally.
    /** Radius (blocks) within which a Hurleur hands its target to nearby idle zombies. */
    public static double specialScreamerRadius = 24.0;
    // -- Soigneur (ACTIVE): heal aura.
    /** Radius (blocks) of the Soigneur's regeneration aura. */
    public static double specialHealerRadius = 8.0;
    /** Duration (ticks) of the Regeneration granted by a Soigneur. */
    public static int specialHealerRegenTicks = 100;
    /** Amplifier of the Regeneration granted by a Soigneur (0 = Regeneration I). */
    public static int specialHealerRegenAmp = 0;
    // -- Nécromancien (ACTIVE): summon.
    /** Minimum children a Nécromancien summons per activation. */
    public static int specialNecromancerMinChildren = 1;
    /** Maximum children a Nécromancien summons per activation. */
    public static int specialNecromancerMaxChildren = 2;
    /** Skip the summon if more than this many zombies are already within the density radius. */
    public static int specialNecromancerDensityCap = 40;
    /** Radius (blocks) of the local-density check that gates a Nécromancien summon. */
    public static double specialNecromancerDensityRadius = 12.0;
    /** Scatter (blocks) of summoned children around the Nécromancien. */
    public static int specialNecromancerSpread = 2;
    // -- Splitter (DEATH): split on death.
    /** Number of small children a Splitter spawns when it dies. */
    public static int specialSplitterChildren = 2;
    /** Size multiplier of each Splitter child (0.6 = 60% size). */
    public static double specialSplitterChildScale = 0.6;
    /** Scatter (blocks) of the children a Splitter spawns on death. */
    public static int specialSplitterSpread = 1;
    // -- Sprinteur (PASSIVE): speed.
    /** Speed effect amplifier on a Sprinteur (1 = Speed II). */
    public static int specialSprinterSpeedAmp = 1;
    /** Movement-speed attribute multiplier on a Sprinteur. */
    public static double specialSprinterSpeedMul = 1.35;
    // -- Bondisseur (PASSIVE): leap.
    /** LEAP effect amplifier on a Bondisseur (2 = LEAP III). */
    public static int specialLeaperLeapAmp = 2;
    // -- Juggernaut (PASSIVE): tank.
    /** Size multiplier on a Juggernaut. */
    public static double specialJuggernautScale = 1.4;
    /** Max-health multiplier on a Juggernaut. */
    public static double specialJuggernautHealthMul = 2.0;
    /** Resistance effect amplifier on a Juggernaut (1 = Resistance II). */
    public static int specialJuggernautResistanceAmp = 1;
}
