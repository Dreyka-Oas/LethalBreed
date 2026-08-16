package com.dreykaoas.lethalbreed.special.runtime;

import com.dreykaoas.lethalbreed.config.domain.SpecialVariantConfig;

/**
 * Pure maths behind a Bombeur detonation: how long its fuse burns, how hard it blows, how far the gore
 * reaches, and what that gore does to whoever it lands on.
 *
 * <p>Deliberately free of every {@code net.minecraft} type. The entity-facing half lives in
 * {@link SpecialAbilities}; keeping the numbers here is what lets them be unit-tested without booting a
 * server, and this is the only part of the Bombeur that carries non-trivial logic.
 *
 * <p>The shaping constants below are NOT config options. Only the levers in {@link SpecialVariantConfig}
 * are exposed: the mod already ships hundreds of options, and surfacing every coefficient of a curve would
 * make the variant impossible for a player to balance. The blindness THRESHOLD is configurable because it
 * decides whether the harshest effect appears at all; the shape coefficients only decide dosage.
 *
 * <p>Every min/max pair is read through {@link #lo}/{@link #hi} rather than trusted in order: a player who
 * types the bounds the wrong way round gets the range they obviously meant, not a negative ratio.
 */
public final class BombeurBlast {
    private BombeurBlast() {}

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

    /** Intensity at or above which Poison steps from amplifier 0 to 1. */
    static final double POISON_AMP_STEP = 0.6;
    /** Slowness amplifier ceiling — Slowness III would make escape hopeless rather than hard. */
    static final int SLOW_AMP_MAX = 2;

    private static final double NAUSEA_BASE_S = 4.0, NAUSEA_SPAN_S = 11.0;
    private static final double DOT_BASE_S = 3.0, DOT_SPAN_S = 9.0;
    private static final double BLIND_BASE_S = 1.0, BLIND_SPAN_S = 4.0;
    /** Weight of the fuse in the intensity blend; proximity always keeps the remaining share, so distance
     *  can never stop mattering however long the Bombeur swelled. */
    private static final double FUSE_WEIGHT = 0.6;

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
        double a = SpecialVariantConfig.specialBombeurFuseMinTicks;
        double b = SpecialVariantConfig.specialBombeurFuseMaxTicks;
        double min = lo(a, b), max = hi(a, b);
        return (int) Math.round(min + (max - min) * Math.clamp(rand01, 0.0, 1.0));
    }

    /** Where a fuse length sits in its configured range. A degenerate range yields 0 — the mildest blast,
     *  which is the safe way to fail. */
    public static double ratioOf(int fuseTicks) {
        double a = SpecialVariantConfig.specialBombeurFuseMinTicks;
        double b = SpecialVariantConfig.specialBombeurFuseMaxTicks;
        double min = lo(a, b), max = hi(a, b);
        if (max - min <= 0.0) {
            return 0.0;
        }
        return Math.clamp((fuseTicks - min) / (max - min), 0.0, 1.0);
    }

    /** Explosion power for a fuse ratio: the longer it swelled, the bigger it bursts. */
    public static double powerFor(double ratio) {
        double a = SpecialVariantConfig.specialBombeurPowerMin;
        double b = SpecialVariantConfig.specialBombeurPowerMax;
        double min = lo(a, b), max = hi(a, b);
        return min + (max - min) * Math.clamp(ratio, 0.0, 1.0);
    }

    /** Vanilla explosions reach twice their power. */
    public static double blastRadius(double power) {
        return power * 2.0;
    }

    /** The gore ring — wider than the blast, so backing out of lethal range still gets you splattered. */
    public static double splatterRadius(double power) {
        return blastRadius(power) * Math.max(0.0, SpecialVariantConfig.specialBombeurSplatterMul);
    }

    /** How hard the splatter lands: proximity dominates, fuse length amplifies. 0 at the ring's edge. */
    public static double intensity(double ratio, double dist, double splatterRadius) {
        if (splatterRadius <= 0.0) {
            return 0.0;
        }
        double prox = Math.max(0.0, 1.0 - dist / splatterRadius);
        return Math.clamp(prox * ((1.0 - FUSE_WEIGHT) + FUSE_WEIGHT * Math.clamp(ratio, 0.0, 1.0)), 0.0, 1.0);
    }

    private static int seconds(double base, double span, double intensity) {
        return (int) Math.round((base + span * Math.clamp(intensity, 0.0, 1.0)) * TPS);
    }

    public static int nauseaTicks(double i) {
        return seconds(NAUSEA_BASE_S, NAUSEA_SPAN_S, i);
    }

    public static int poisonTicks(double i) {
        return seconds(DOT_BASE_S, DOT_SPAN_S, i);
    }

    public static int poisonAmp(double i) {
        return i < POISON_AMP_STEP ? 0 : 1;
    }

    public static int slowTicks(double i) {
        return seconds(DOT_BASE_S, DOT_SPAN_S, i);
    }

    public static int slowAmp(double i) {
        return Math.min(SLOW_AMP_MAX, (int) Math.floor(Math.clamp(i, 0.0, 1.0) * 3.0));
    }

    /** Blindness ticks, or 0 below the configured threshold. A threshold of 1.0 disables it outright — no
     *  intensity can exceed 1 — and that same guard keeps the span division safe. */
    public static int blindTicks(double i) {
        double t = Math.clamp(SpecialVariantConfig.specialBombeurBlindThreshold, 0.0, 1.0);
        if (t >= 1.0 || i < t) {
            return 0;
        }
        return seconds(BLIND_BASE_S, BLIND_SPAN_S, (i - t) / (1.0 - t));
    }

    public static double infectChance(double i) {
        return Math.clamp(i, 0.0, 1.0)
                * Math.clamp(SpecialVariantConfig.specialBombeurInfectChance, 0.0, 1.0);
    }
}
