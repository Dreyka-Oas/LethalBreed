package oas.dreyka.lethalbreed.special.runtime;

import oas.dreyka.lethalbreed.config.domain.SpecialVariantConfig;

/**
 * Pure maths behind a Bomber detonation: how long its fuse burns, how hard it blows, how far the gore
 * reaches, and what that gore does to whoever it lands on.
 *
 * <p>Deliberately free of every {@code net.minecraft} type. The entity-facing half lives in
 * {@link SpecialAbilities}; keeping the numbers here is what lets them be unit-tested without booting a
 * server, and the Bomber's non-trivial logic all lives here.
 *
 * <p>The shaping constants below are NOT config options. Only the levers in {@link SpecialVariantConfig}
 * are exposed: the mod already ships hundreds of options, and surfacing every coefficient of a curve would
 * make the variant impossible for a player to balance. The blindness THRESHOLD is configurable because it
 * decides whether the harshest effect appears at all; the shape coefficients only decide dosage.
 *
 * <p>Every min/max pair is read through {@link #lo}/{@link #hi}, so their order is never trusted: a player
 * who types the bounds the wrong way round gets the range they obviously meant, not a negative ratio.
 */
public final class BomberBlast {
    private BomberBlast() {}

    /**
     * Colour of the splatter particle cloud, packed <b>ARGB</b>: toxic purple-green, distinct from any
     * vanilla potion colour so the burst reads as this mod's own effect, never as a thrown potion.
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

    private static double lo(double a, double b) { return Math.min(a, b); }

    private static double hi(double a, double b) { return Math.max(a, b); }

    /**
     * Fuse length in GAME TICKS for a uniform roll in {@code [0,1]}.
     *
     * <p>Ticks, not activations. {@code SpecialBehavior.tick} only runs once every {@code tickBuckets}
     * ticks, so the old per-activation charge tied a gameplay tempo to a performance knob: raising
     * {@code tickBuckets} silently doubled the time before detonation. The caller turns this figure into an
     * absolute deadline, so the duration no longer follows activation cadence.
     */
    public static int fuseTicksFor(double rand01) {
        double a = SpecialVariantConfig.specialBomberFuseMinTicks;
        double b = SpecialVariantConfig.specialBomberFuseMaxTicks;
        double min = lo(a, b), max = hi(a, b);
        return (int) Math.round(min + (max - min) * Math.clamp(rand01, 0.0, 1.0));
    }

    /** Where a fuse length sits in its configured range. A degenerate range yields 0, the mildest blast:
     *  the safe way to fail. */
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

    /** The gore ring: wider than the blast, so backing out of lethal range still gets you splattered. */
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


    public static double infectChance(double i) {
        return Math.clamp(i, 0.0, 1.0)
                * Math.clamp(SpecialVariantConfig.specialBomberInfectChance, 0.0, 1.0);
    }
}
