package com.dreykaoas.lethalbreed.special.runtime;

import com.dreykaoas.lethalbreed.config.domain.SpecialVariantConfig;

/**
 * Pure maths behind a Bomber detonation: how long its fuse burns, how hard it blows, how far the gore
 * reaches, and what that gore does to whoever it lands on.
 *
 * <p>Deliberately free of every {@code net.minecraft} type. The entity-facing half lives in
 * {@link SpecialAbilities}; keeping the numbers here is what lets them be unit-tested without booting a
 * server, and this is the only part of the Bomber that carries non-trivial logic.
 *
 * <p>The shaping constants below are NOT config options. Only the levers in {@link SpecialVariantConfig}
 * are exposed: the mod already ships hundreds of options, and surfacing every coefficient of a curve would
 * make the variant impossible for a player to balance. The blindness THRESHOLD is configurable because it
 * decides whether the harshest effect appears at all; the shape coefficients only decide dosage.
 *
 * <p>Every min/max pair is read through {@link #lo}/{@link #hi} rather than trusted in order: a player who
 * types the bounds the wrong way round gets the range they obviously meant, not a negative ratio.
 */
public final class BomberBlast {
    private BomberBlast() {}

    /**
     * Colour of the splatter particle cloud, packed <b>ARGB</b> — toxic purple-green, distinct from any
     * vanilla potion colour so the burst reads as this mod's own effect rather than as a thrown potion.
     *
     * <p>The {@code 0xFF} alpha is load-bearing, not decoration. {@code ENTITY_EFFECT} carries a packed ARGB
     * int and {@code SpellParticle.MobEffectProvider} feeds its alpha byte straight into {@code setAlpha}, so
     * a bare {@code 0xRRGGBB} literal yields alpha 0 and the cloud renders perfectly invisible: the particles
     * spawn, tick and expire without ever drawing a pixel. That is exactly how this shipped the first time,
     * and it is why the constant lives here, where a unit test can hold it to being opaque.
     */
    public static final int SPLATTER_COLOR_ARGB = 0xFF8A2E7A;


    /** Weight of the fuse in the intensity blend; proximity always keeps the remaining share, so distance
     *  can never stop mattering however long the Bomber swelled. */
    private static final double FUSE_WEIGHT = 0.6;

    /** Lingering gore puddle: how long the residue stays on the ground, floor and fuse-driven span. */
    private static final double PUDDLE_BASE_S = 3.0, PUDDLE_SPAN_S = 9.0;
    /** The puddle pools tighter than the airborne ring — gore falls inward, it does not hang where it flew. */
    private static final double PUDDLE_RADIUS_MUL = 0.6;
    /** Residue bites softer than the burst that threw it. Halving keeps the puddle a hazard to linger in
     *  rather than a second explosion. */
    private static final double PUDDLE_POTENCY = 0.5;
    /** How often the puddle re-doses whoever is standing in it — the cadence vanilla lingering clouds use. */
    public static final int PUDDLE_REAPPLY_TICKS = 20;

    private static final int TPS = 20;

    private static double lo(double a, double b) { return Math.min(a, b); }

    private static double hi(double a, double b) { return Math.max(a, b); }

    /**
     * Fuse length in GAME TICKS for a uniform roll in {@code [0,1]}.
     *
     * <p>Ticks, not activations. {@code SpecialBehavior.tick} only runs once every {@code tickBuckets}
     * ticks, so the old per-activation charge tied a gameplay tempo to a performance knob — raising
     * {@code tickBuckets} silently doubled the time before detonation. The caller turns this figure into an
     * absolute deadline, which is what makes the duration independent of activation cadence.
     */
    public static int fuseTicksFor(double rand01) {
        double a = SpecialVariantConfig.specialBomberFuseMinTicks;
        double b = SpecialVariantConfig.specialBomberFuseMaxTicks;
        double min = lo(a, b), max = hi(a, b);
        return (int) Math.round(min + (max - min) * Math.clamp(rand01, 0.0, 1.0));
    }

    /** Where a fuse length sits in its configured range. A degenerate range yields 0 — the mildest blast,
     *  which is the safe way to fail. */
    public static double ratioOf(int fuseTicks) {
        double a = SpecialVariantConfig.specialBomberFuseMinTicks;
        double b = SpecialVariantConfig.specialBomberFuseMaxTicks;
        double min = lo(a, b), max = hi(a, b);
        if (max - min <= 0.0) {
            return 0.0;
        }
        return Math.clamp((fuseTicks - min) / (max - min), 0.0, 1.0);
    }

    /** Explosion power for a fuse ratio: the longer it swelled, the bigger it bursts. */
    public static double powerFor(double ratio) {
        double a = SpecialVariantConfig.specialBomberPowerMin;
        double b = SpecialVariantConfig.specialBomberPowerMax;
        double min = lo(a, b), max = hi(a, b);
        return min + (max - min) * Math.clamp(ratio, 0.0, 1.0);
    }

    /** Vanilla explosions reach twice their power. */
    public static double blastRadius(double power) {
        return power * 2.0;
    }

    /** The gore ring — wider than the blast, so backing out of lethal range still gets you splattered. */
    public static double splatterRadius(double power) {
        return blastRadius(power) * Math.max(0.0, SpecialVariantConfig.specialBomberSplatterMul);
    }

    /** How hard the splatter lands: proximity dominates, fuse length amplifies. 0 at the ring's edge. */
    public static double intensity(double ratio, double dist, double splatterRadius) {
        if (splatterRadius <= 0.0) {
            return 0.0;
        }
        double prox = Math.max(0.0, 1.0 - dist / splatterRadius);
        return Math.clamp(prox * ((1.0 - FUSE_WEIGHT) + FUSE_WEIGHT * Math.clamp(ratio, 0.0, 1.0)), 0.0, 1.0);
    }

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
     * {@link #PUDDLE_POTENCY}. Reusing {@link #intensity} is deliberate — the residue should fall off toward
     * its own edge exactly the way the ring does, so one rule governs both and there is no second curve to
     * keep in sync.
     */
    public static double puddleIntensity(double ratio, double dist, double puddleRadius) {
        return intensity(ratio, dist, puddleRadius) * PUDDLE_POTENCY;
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

    public static double infectChance(double i) {
        return Math.clamp(i, 0.0, 1.0)
                * Math.clamp(SpecialVariantConfig.specialBomberInfectChance, 0.0, 1.0);
    }
}
