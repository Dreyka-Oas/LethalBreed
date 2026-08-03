package com.dreykaoas.lethalbreed.config.bounds;

import com.dreykaoas.lethalbreed.config.BoundsRegistrar;

/**
 * Clamp ranges for the phase, special-variant and dev options.
 *
 * <p>Split out of {@code ConfigBoundsTable}, which had grown to 305 append-only lines across nine unrelated
 * domains. Registration order does not matter — the table is a map keyed by lower-cased option name — but
 * grouping does: a bound belongs next to the options it governs, and {@code ConfigBoundsTest} fails the build
 * if any numeric option loses one.
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
        r.b("specialBaseChance", 0, 1);
        r.b("specialPhaseScale", 0, 1);
        r.b("specialMaxChance", 0, 1);
        r.b("specialActionInterval", 1, 1000);
        // Per-type unlock phase (0 = always available) + selection weight (0 = never picked).
        r.b("specialSprinteurPhase", 0, 1_000_000);
        r.b("specialSprinteurWeight", 0, 1_000_000);
        r.b("specialBondisseurPhase", 0, 1_000_000);
        r.b("specialBondisseurWeight", 0, 1_000_000);
        r.b("specialBombeurPhase", 0, 1_000_000);
        r.b("specialBombeurWeight", 0, 1_000_000);
        r.b("specialHurleurPhase", 0, 1_000_000);
        r.b("specialHurleurWeight", 0, 1_000_000);
        r.b("specialSoigneurPhase", 0, 1_000_000);
        r.b("specialSoigneurWeight", 0, 1_000_000);
        r.b("specialJuggernautPhase", 0, 1_000_000);
        r.b("specialJuggernautWeight", 0, 1_000_000);
        r.b("specialNecromancienPhase", 0, 1_000_000);
        r.b("specialNecromancienWeight", 0, 1_000_000);
        r.b("specialSplitterPhase", 0, 1_000_000);
        r.b("specialSplitterWeight", 0, 1_000_000);
        // Per-type behaviour magnitudes.
        r.b("specialBombeurPower", 0, 100);
        r.b("specialBombeurArmRange", 0, 64);
        r.b("specialBombeurFusePerTick", 0.001, 1.0);
        r.b("specialHurleurRadius", 0, 128);
        r.b("specialSoigneurRadius", 0, 128);
        r.b("specialSoigneurRegenTicks", 1, 72_000);
        r.b("specialSoigneurRegenAmp", 0, 9);
        r.b("specialNecromancienMinChildren", 0, 64);
        r.b("specialNecromancienMaxChildren", 0, 64);
        r.b("specialNecromancienDensityCap", 0, 10_000);
        r.b("specialNecromancienDensityRadius", 0, 128);
        r.b("specialNecromancienSpread", 0, 16);
        r.b("specialSplitterChildren", 0, 64);
        r.b("specialSplitterChildScale", 0.05, 10);
        r.b("specialSplitterSpread", 0, 16);
        r.b("specialSprinteurSpeedAmp", 0, 9);
        r.b("specialSprinteurSpeedMul", 0, 10);
        r.b("specialBondisseurLeapAmp", 0, 9);
        r.b("specialJuggernautScale", 0.05, 10);
        r.b("specialJuggernautHealthMul", 0.05, 100);
        r.b("specialJuggernautResistanceAmp", 0, 9);
        r.b("devSpawnRadius", 1, 256);

    }
}
