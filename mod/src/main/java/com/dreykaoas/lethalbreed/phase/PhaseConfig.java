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
 *
 * <p>The stat curves are then bent onto a ceiling by {@link #softCap}, because "accelerating forever" meant
 * a phase-100 zombie hitting for 391 raw — a guaranteed one-shot through full netherite. The bend starts
 * only above {@link #KNEE_PHASE}, so that same tuned phase-15 table is still reproduced exactly.
 *
 * <p>These ceilings bound the MULTIPLIER, which is not the same as bounding the attribute: a rolled Strength
 * is an {@code ADD_VALUE} that lands in the base the multiplier scales, so it re-crosses any ceiling set
 * here. {@code AttributeCaps} is what actually guarantees the no-one-shot promise; this class only keeps the
 * curve itself sane.
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

    /**
     * The phase whose values the shipped stat curves were hand-tuned to reproduce, and therefore the phase up
     * to which {@link #softCap} must change nothing at all.
     *
     * <p>Not a config option on purpose. It is not a balance lever — it is the anchor that makes the ceilings
     * a pure addition rather than a rebalance of every existing world, and moving it would silently re-tune
     * phases 1-15 for everyone. The ceilings themselves are the levers.
     */
    static final int KNEE_PHASE = 15;

    /**
     * Bend an unbounded stat curve onto a ceiling it approaches but never reaches.
     *
     * <p>Below {@code knee} the value is returned untouched, so the hand-tuned low phases are bit-for-bit
     * what they were. Above it the curve decays exponentially onto {@code ceiling}. The two halves meet with
     * the same value AND the same slope (the exponential's derivative at the knee is exactly 1), so there is
     * no visible kink at the join — a player climbing through phase 15 feels nothing happen.
     *
     * <p>A hard {@code Math.min} was rejected deliberately: it would make every phase past the crossing point
     * identical, erasing progression entirely. This keeps phases 30, 50 and 100 distinct while bounding them.
     *
     * <p>Degenerate input is the caller's mistake, not a crash: a ceiling at or below the knee cannot express
     * "approach from below", so the knee is returned as the flat bound.
     */
    static double softCap(double v, double knee, double ceiling) {
        if (v <= knee) {
            return v;
        }
        double headroom = ceiling - knee;
        if (headroom <= 0.0) {
            return knee;
        }
        return ceiling - headroom * Math.exp(-(v - knee) / headroom);
    }

    /** The knee for one stat curve: what that curve produced at {@link #KNEE_PHASE} before any ceiling. */
    private static double cappedStat(double growth, double exponent, int phase, double ceiling) {
        return softCap(stat(growth, exponent, phase), stat(growth, exponent, KNEE_PHASE), ceiling);
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    /** Phase 0 = "Classic" (no bonus, no hostile spawns — handled in the spawn hook). Phase is unbounded
     *  above; every field is computed live from the configured curve. */
    public static PhaseDef def(int phase) {
        int p = Math.max(0, phase);

        // Each pair shares its ceiling: min and max are two ends of one roll range, and letting them saturate
        // onto different bounds would let the min overtake the max at high phase.
        double hpCeil = ProgressionConfig.phaseHpCeiling;
        double dmgCeil = ProgressionConfig.phaseDmgCeiling;
        double spdCeil = ProgressionConfig.phaseSpdCeiling;

        double hpMax = cappedStat(ProgressionConfig.phaseHpMaxGrowth, ProgressionConfig.phaseHpExponent, p, hpCeil);
        double hpMin = cappedStat(ProgressionConfig.phaseHpMinGrowth, ProgressionConfig.phaseHpExponent, p, hpCeil);
        double dmgMax = cappedStat(ProgressionConfig.phaseDmgMaxGrowth, ProgressionConfig.phaseDmgExponent, p, dmgCeil);
        double dmgMin = cappedStat(ProgressionConfig.phaseDmgMinGrowth, ProgressionConfig.phaseDmgExponent, p, dmgCeil);
        double spdMax = cappedStat(ProgressionConfig.phaseSpdMaxGrowth, ProgressionConfig.phaseSpdExponent, p, spdCeil);
        double spdMin = cappedStat(ProgressionConfig.phaseSpdMinGrowth, ProgressionConfig.phaseSpdExponent, p, spdCeil);

        double effChance = clamp01(sat(1.0, ProgressionConfig.phaseEffChanceDecay, p));
        int effCount = (int) Math.floor(
                sat(ProgressionConfig.phaseEffCountCeiling, ProgressionConfig.phaseEffCountDecay, p));
        int effMaxAmp = (int) Math.floor(
                sat(ProgressionConfig.phaseEffAmpCeiling, ProgressionConfig.phaseEffAmpDecay, p));

        return new PhaseDef("Phase " + p, hpMin, hpMax, dmgMin, dmgMax, spdMin, spdMax,
                effChance, effCount, effMaxAmp);
    }
}
