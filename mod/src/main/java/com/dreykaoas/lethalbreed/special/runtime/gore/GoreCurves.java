package com.dreykaoas.lethalbreed.special.runtime.gore;

import com.dreykaoas.lethalbreed.config.domain.SpecialVariantConfig;
import com.dreykaoas.lethalbreed.special.runtime.BomberBlast;

/**
 * The curves that shape the aftermath of a burst rather than the burst itself: how long a puddle lasts and
 * how it shrinks, how many afflictions a phase puts in the cocktail and how strong they get, and how long
 * each one lands for.
 *
 * <p>Pure arithmetic, no world access, so the whole set stays unit-testable without booting a server.
 * {@link BomberBlast} keeps the blast: fuse, power, radius, and the distance falloff these reuse.
 */
public final class GoreCurves {
    private GoreCurves() {}

    /** Seconds a puddle lasts at the shortest and the longest fuse. */
    private static final double PUDDLE_BASE_S = 3.0, PUDDLE_SPAN_S = 9.0;
    /** A puddle is smaller than the ring that made it: the gore settles inward as it lands. */
    private static final double PUDDLE_RADIUS_MUL = 0.6;
    /** The residue doses at half the strength of the burst that left it. */
    private static final double PUDDLE_POTENCY = 0.5;
    /** How often a lingering puddle re-doses whoever stands in it. */
    public static final int PUDDLE_REAPPLY_TICKS = 20;
    private static final int TPS = 20;

    /** How long the gore puddle lingers, in ticks. A long fuse leaves more of a mess behind. */
    public static int puddleDurationTicks(double ratio) {
        return effectTicks(PUDDLE_BASE_S, PUDDLE_SPAN_S, ratio);
    }

    /** The puddle's radius at the moment it forms. */
    public static double puddleRadius(double splatterRadius) {
        return Math.max(0.0, splatterRadius) * PUDDLE_RADIUS_MUL;
    }

    /**
     * The puddle's radius after {@code age} ticks, shrinking linearly so it reaches exactly 0 as it expires —
     * the visual and the hazard drain together, and nobody gets clipped by a puddle they can no longer see.
     */
    public static double puddleRadiusAt(double radius0, int age, int durationTicks) {
        if (durationTicks <= 0 || age >= durationTicks) {
            return 0.0;
        }
        return Math.max(0.0, radius0) * (1.0 - (double) Math.max(0, age) / durationTicks);
    }

    /**
     * Dose delivered by standing in the puddle: the same proximity-and-fuse curve as the burst, scaled down by
     * {@link #PUDDLE_POTENCY}. Reusing {@link BomberBlast#intensity} is deliberate — the residue should fall off toward
     * its own edge exactly the way the ring does, so one rule governs both and there is no second curve to
     * keep in sync.
     */
    public static double puddleIntensity(double ratio, double dist, double puddleRadius) {
        return BomberBlast.intensity(ratio, dist, puddleRadius) * PUDDLE_POTENCY;
    }

    /**
     * How many distinct effects this Bomber's gore cocktail carries, given the phase.
     *
     * <p>Always at least one — a Bomber that splatters nothing is a firework — rising toward
     * {@code specialBomberEffectCountCeiling} on the same saturating shape the rest of the phase system
     * uses. Rounded rather than floored so the ceiling is actually reachable: {@code (C-1)·(1-decay^p)}
     * approaches {@code C-1} from below and would floor to {@code C-2} forever.
     */
    public static int cocktailSize(int phase) {
        int ceiling = Math.max(1, SpecialVariantConfig.specialBomberEffectCountCeiling);
        double grown = (ceiling - 1) * saturation(SpecialVariantConfig.specialBomberEffectCountDecay, phase);
        return 1 + (int) Math.round(grown);
    }

    /**
     * Highest amplifier the cocktail may roll at this phase. The caller draws uniformly in {@code [0, this]},
     * so the amplifier is random per effect while the ceiling itself is a function of the phase.
     */
    public static int cocktailMaxAmp(int phase) {
        int ceiling = Math.max(0, SpecialVariantConfig.specialBomberEffectAmpCeiling);
        return (int) Math.round(ceiling * saturation(SpecialVariantConfig.specialBomberEffectAmpDecay, phase));
    }

    /** {@code 1 - decay^phase}: 0 at phase 0, approaching 1. A decay outside (0,1) would not saturate, so it
     *  is clamped into the range the config bounds already advertise. */
    private static double saturation(double decay, int phase) {
        double d = Math.clamp(decay, 0.0, 0.999);
        return 1.0 - Math.pow(d, Math.max(0, phase));
    }

    /**
     * Duration in ticks for one affliction: {@code base + span * intensity} seconds.
     *
     * <p>Public and generic because the effect set is now rolled per Bomber — each pool entry in
     * {@code GoreCocktail} brings its own base/span pair, instead of every effect owning a bespoke named
     * shaper here. Distance and fuse length still set the intensity and therefore still set the duration;
     * only WHICH effects land became random.
     */
    public static int effectTicks(double base, double span, double intensity) {
        return (int) Math.round((base + span * Math.clamp(intensity, 0.0, 1.0)) * TPS);
    }

    /**
     * Whether Blindness is eligible for this blast's cocktail at all.
     *
     * <p>Kept here rather than inline in {@code GoreCocktail} so the meaning of
     * {@code specialBomberBlindThreshold} — "intensity from which Blindness is applied, 1.0 disables it" —
     * stays testable without booting a server. Blindness is the one entry in the pool that removes
     * information rather than capability, which is why it alone is gated.
     */
    public static boolean blindnessEligible(double intensity) {
        double t = Math.clamp(SpecialVariantConfig.specialBomberBlindThreshold, 0.0, 1.0);
        // No intensity can exceed 1, so a threshold of 1.0 disables Blindness outright.
        return t < 1.0 && intensity >= t;
    }
}
