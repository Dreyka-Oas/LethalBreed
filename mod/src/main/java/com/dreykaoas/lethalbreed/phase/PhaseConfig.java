package com.dreykaoas.lethalbreed.phase;

import com.dreykaoas.lethalbreed.config.domain.ProgressionConfig;

/**
 * The escalation curve as FORMULAS of the phase number, not a static table — so difficulty keeps rising
 * forever instead of plateauing past some fixed phase count. Each {@link PhaseDef} holds the per-zombie
 * roll RANGES (which widen with phase = "plus haut, plus random") and the beneficial-effect parameters
 * (which rise = "plus agressif"). Zombies carry no gear, so there are no armor/weapon/enchant fields.
 *
 * <p>Stat curves (hp/dmg/spd) use an accelerating power curve {@code 1.0 + growth * phase^exponent};
 * effect curves use a saturating curve {@code ceiling - ceiling * decay^phase} (rises then approaches
 * a ceiling). All growth/decay/ceiling parameters live in {@link ProgressionConfig} and are tuned so
 * {@code def(15)} reproduces the old hand-tuned table almost exactly — no difficulty jump for a world
 * already at phase 15 when this ships.
 */
public final class PhaseConfig {
    private PhaseConfig() {}

    public record PhaseDef(
            String name,
            double hpMin, double hpMax,
            double dmgMin, double dmgMax,
            double spdMin, double spdMax,
            double effChance, int effCount, int effMaxAmp) {}

    /** Accelerating stat curve: {@code 1.0 + growth * phase^exponent}. */
    private static double stat(double growth, double exponent, int phase) {
        return 1.0 + growth * Math.pow(phase, exponent);
    }

    /** Saturating effect curve: rises from 0 toward {@code ceiling}, never exceeding it. */
    private static double sat(double ceiling, double decay, int phase) {
        return ceiling - ceiling * Math.pow(decay, phase);
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    /** Phase 0 = "Classic" (no bonus, no hostile spawns — handled in the spawn hook). Phase is unbounded
     *  above; every field is computed live from the configured curve. */
    public static PhaseDef def(int phase) {
        int p = Math.max(0, phase);

        double hpMax = stat(ProgressionConfig.phaseHpMaxGrowth, ProgressionConfig.phaseHpExponent, p);
        double hpMin = stat(ProgressionConfig.phaseHpMinGrowth, ProgressionConfig.phaseHpExponent, p);
        double dmgMax = stat(ProgressionConfig.phaseDmgMaxGrowth, ProgressionConfig.phaseDmgExponent, p);
        double dmgMin = stat(ProgressionConfig.phaseDmgMinGrowth, ProgressionConfig.phaseDmgExponent, p);
        double spdMax = stat(ProgressionConfig.phaseSpdMaxGrowth, ProgressionConfig.phaseSpdExponent, p);
        double spdMin = stat(ProgressionConfig.phaseSpdMinGrowth, ProgressionConfig.phaseSpdExponent, p);

        double effChance = clamp01(sat(1.0, ProgressionConfig.phaseEffChanceDecay, p));
        int effCount = (int) Math.floor(
                sat(ProgressionConfig.phaseEffCountCeiling, ProgressionConfig.phaseEffCountDecay, p));
        int effMaxAmp = (int) Math.floor(
                sat(ProgressionConfig.phaseEffAmpCeiling, ProgressionConfig.phaseEffAmpDecay, p));

        return new PhaseDef("Phase " + p, hpMin, hpMax, dmgMin, dmgMax, spdMin, spdMax,
                effChance, effCount, effMaxAmp);
    }
}
