package com.dreykaoas.lethalbreed.config.bounds;

import com.dreykaoas.lethalbreed.config.BoundsRegistrar;

/**
 * Clamp ranges for the phase and special-variant options.
 *
 * <p>Split out of {@code ConfigBoundsTable}, which had grown to 305 append-only lines across nine unrelated
 * domains: a bound belongs next to the options it governs, and {@code ConfigBoundsTest} fails the build if
 * any numeric option loses one.
 */
public final class ProgressionBounds {
    private ProgressionBounds() {}

    public static void register(BoundsRegistrar r) {
        r.b("phaseIntervalTicks", 1, 1_000_000);
        r.b("phaseJitterTicks", 0, 1_000_000);
        r.b("phaseHpMaxGrowth", 0, 10);
        r.b("phaseHpMinGrowth", 0, 10);
        r.b("phaseHpExponent", 0.5, 3.0);
        r.b("phaseDmgMaxGrowth", 0, 10);
        r.b("phaseDmgMinGrowth", 0, 10);
        r.b("phaseDmgExponent", 0.5, 3.0);
        r.b("phaseSpdMaxGrowth", 0, 10);
        r.b("phaseSpdMinGrowth", 0, 10);
        r.b("phaseSpdExponent", 0.5, 3.0);
        r.b("phaseEffChanceDecay", 0.5, 0.999);
        r.b("phaseEffCountDecay", 0.5, 0.999);
        r.b("phaseEffCountCeiling", 1, 9);
        r.b("phaseEffAmpDecay", 0.5, 0.999);
        r.b("phaseEffAmpCeiling", 0, 10);
        r.b("phaseMobcapGrowth", 0, 100);
        r.b("phaseMobcapExponent", 0.5, 3.0);
        r.b("phaseFrequencyGrowth", 0, 100);
        r.b("phaseFrequencyExponent", 0.5, 3.0);
        r.b("phaseMax", 1, 1_000_000);
        // Curve ceilings. Lower bound 1.0: these bound a MULTIPLIER, and below 1.0 a zombie would end up
        // weaker than a vanilla one the higher the phase climbed.
        r.b("phaseDmgCeiling", 1.0, 1000.0);
        r.b("phaseHpCeiling", 1.0, 1000.0);
        r.b("phaseSpdCeiling", 1.0, 100.0);
        r.b("phaseMobcapCeiling", 1.0, 1000.0);
        r.b("phaseFrequencyCeiling", 1.0, 1000.0);
        // Final attribute caps. Floors are deliberately non-zero: a cap of 0 would make zombies harmless
        // rather than merely fair, and a 0 health cap would kill every zombie the instant it spawned.
        r.b("phaseDamageCap", 0.5, 1000.0);
        r.b("phaseHealthCap", 1.0, 100_000.0);
        r.b("phaseSpeedCap", 0.05, 10.0);
        r.b("specialBaseChance", 0, 1);
        r.b("specialPhaseScale", 0, 1);
        r.b("specialMaxChance", 0, 1);
        r.b("specialActionInterval", 1, 1000);
        // Per-type unlock phase (0 = always available) + selection weight (0 = never picked).
        r.b("specialSprinterPhase", 0, 1_000_000);
        r.b("specialSprinterWeight", 0, 1_000_000);
        r.b("specialLeaperPhase", 0, 1_000_000);
        r.b("specialLeaperWeight", 0, 1_000_000);
        r.b("specialBomberPhase", 0, 1_000_000);
        r.b("specialBomberWeight", 0, 1_000_000);
        r.b("specialScreamerPhase", 0, 1_000_000);
        r.b("specialScreamerWeight", 0, 1_000_000);
        r.b("specialHealerPhase", 0, 1_000_000);
        r.b("specialHealerWeight", 0, 1_000_000);
        r.b("specialJuggernautPhase", 0, 1_000_000);
        r.b("specialJuggernautWeight", 0, 1_000_000);
        r.b("specialNecromancerPhase", 0, 1_000_000);
        r.b("specialNecromancerWeight", 0, 1_000_000);
        r.b("specialSplitterPhase", 0, 1_000_000);
        r.b("specialSplitterWeight", 0, 1_000_000);
        // Per-type behaviour magnitudes.
        r.b("specialBomberArmRange", 0, 64);
        r.b("specialBomberFuseMinTicks", 1, 6_000);
        r.b("specialBomberFuseMaxTicks", 1, 6_000);
        r.b("specialBomberPowerMin", 0, 100);
        r.b("specialBomberPowerMax", 0, 100);
        r.b("specialBomberSplatterMul", 0, 10);
        r.b("specialBomberInfectChance", 0, 1);
        r.b("specialBomberBlindThreshold", 0, 1);
        // Cocktail: at least one effect (a Bombeur that splatters nothing is just a firework), and the
        // decays share the 0.5..0.999 window the other phase decays use.
        r.b("specialBomberEffectCountCeiling", 1, 7);
        r.b("specialBomberEffectCountDecay", 0.5, 0.999);
        r.b("specialBomberEffectAmpCeiling", 0, 4);
        r.b("specialBomberEffectAmpDecay", 0.5, 0.999);
        r.b("specialScreamerRadius", 0, 128);
        r.b("specialHealerRadius", 0, 128);
        r.b("specialHealerRegenTicks", 1, 72_000);
        r.b("specialHealerRegenAmp", 0, 9);
        r.b("specialNecromancerMinChildren", 0, 64);
        r.b("specialNecromancerMaxChildren", 0, 64);
        r.b("specialNecromancerDensityCap", 0, 10_000);
        r.b("specialNecromancerDensityRadius", 0, 128);
        r.b("specialNecromancerSpread", 0, 16);
        r.b("specialSplitterChildren", 0, 64);
        r.b("specialSplitterChildScale", 0.05, 10);
        r.b("specialSplitterSpread", 0, 16);
        r.b("specialSprinterSpeedAmp", 0, 9);
        r.b("specialSprinterSpeedMul", 0, 10);
        r.b("specialLeaperLeapAmp", 0, 9);
        r.b("specialJuggernautScale", 0.05, 10);
        r.b("specialJuggernautHealthMul", 0.05, 100);
        r.b("specialJuggernautResistanceAmp", 0, 9);
    }
}
