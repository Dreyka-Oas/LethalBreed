package com.dreykaoas.lethalbreed.phase;

import com.dreykaoas.lethalbreed.config.domain.ProgressionConfig;

/**
 * The escalation curve as FORMULAS of the phase number, not a static table — so difficulty keeps rising
 * forever instead of plateauing past some fixed phase count. Each {@link PhaseDef} holds the per-zombie
 * roll RANGES (which widen with phase = "plus haut, plus random") and the gear/effect parameters (which
 * rise = "plus agressif"). Tier indices map into {@link ZombieEquipper}'s material ladders (0 = wood/
 * leather … 5 = netherite).
 *
 * <p>Stat curves (hp/dmg/spd) use an accelerating power curve {@code 1.0 + growth * phase^exponent};
 * gear/effect curves use a saturating curve {@code ceiling - ceiling * decay^phase} (rises then approaches
 * a ceiling, matching the old table's shape near phase 15 but continuing cleanly to infinity instead of
 * clamping on an array index). All growth/decay/ceiling parameters live in {@link ProgressionConfig} and
 * are tuned so {@code def(15)} reproduces the old hand-tuned table almost exactly — no difficulty jump for
 * a world already at phase 15 when this ships.
 */
public final class PhaseConfig {
    private PhaseConfig() {}

    public record PhaseDef(
            String name,
            double hpMin, double hpMax,
            double dmgMin, double dmgMax,
            double spdMin, double spdMax,
            double armorChance, int armorMaxTier,
            double weaponChance, int weaponMaxTier,
            int enchantLevel,
            double effChance, int effCount, int effMaxAmp) {}

    /** Accelerating stat curve: {@code 1.0 + growth * phase^exponent}. */
    private static double stat(double growth, double exponent, int phase) {
        return 1.0 + growth * Math.pow(phase, exponent);
    }

    /** Saturating gear/effect curve: rises from 0 toward {@code ceiling}, never exceeding it. */
    private static double sat(double ceiling, double decay, int phase) {
        return ceiling - ceiling * Math.pow(decay, phase);
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    /** Phase 0 = "Classic" (no bonus, no gear, no hostile spawns — handled in the spawn hook). Phase is
     *  unbounded above; every field is computed live from the configured curve. */
    public static PhaseDef def(int phase) {
        int p = Math.max(0, phase);

        double hpMax = stat(ProgressionConfig.phaseHpMaxGrowth, ProgressionConfig.phaseHpExponent, p);
        double hpMin = stat(ProgressionConfig.phaseHpMinGrowth, ProgressionConfig.phaseHpExponent, p);
        double dmgMax = stat(ProgressionConfig.phaseDmgMaxGrowth, ProgressionConfig.phaseDmgExponent, p);
        double dmgMin = stat(ProgressionConfig.phaseDmgMinGrowth, ProgressionConfig.phaseDmgExponent, p);
        double spdMax = stat(ProgressionConfig.phaseSpdMaxGrowth, ProgressionConfig.phaseSpdExponent, p);
        double spdMin = stat(ProgressionConfig.phaseSpdMinGrowth, ProgressionConfig.phaseSpdExponent, p);

        double armorChance = clamp01(sat(1.0, ProgressionConfig.phaseArmorChanceDecay, p));
        double weaponChance = clamp01(sat(1.0, ProgressionConfig.phaseWeaponChanceDecay, p));
        // Hard array-bounds clamp [0,5]: ZombieEquipper's material ladders have exactly 6 tiers.
        int armorMaxTier = clampTier((int) Math.floor(sat(5, ProgressionConfig.phaseArmorTierDecay, p)));
        int weaponMaxTier = clampTier((int) Math.floor(sat(5, ProgressionConfig.phaseWeaponTierDecay, p)));
        int enchantLevel = (int) Math.floor(
                sat(ProgressionConfig.phaseEnchantCeiling, ProgressionConfig.phaseEnchantDecay, p));

        double effChance = clamp01(sat(1.0, ProgressionConfig.phaseEffChanceDecay, p));
        int effCount = (int) Math.floor(
                sat(ProgressionConfig.phaseEffCountCeiling, ProgressionConfig.phaseEffCountDecay, p));
        int effMaxAmp = (int) Math.floor(
                sat(ProgressionConfig.phaseEffAmpCeiling, ProgressionConfig.phaseEffAmpDecay, p));

        return new PhaseDef("Phase " + p, hpMin, hpMax, dmgMin, dmgMax, spdMin, spdMax,
                armorChance, armorMaxTier, weaponChance, weaponMaxTier, enchantLevel,
                effChance, effCount, effMaxAmp);
    }

    private static int clampTier(int tier) {
        return Math.max(0, Math.min(5, tier));
    }
}
