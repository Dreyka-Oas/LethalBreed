package com.dreykaoas.lethalbreed.entity;

import com.dreykaoas.lethalbreed.config.domain.ProgressionConfig;
import com.dreykaoas.lethalbreed.util.AttributeModifiers;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * The last word on how hard, how tough and how fast a zombie may ever be, applied once at spawn after every
 * other modifier and effect has landed.
 *
 * <p>The phase curve ceilings in {@code PhaseConfig} bound the MULTIPLIER, which is not the same thing as
 * bounding the attribute. Vanilla computes an attribute as
 * {@code (base + Σ ADD_VALUE) * (1 + Σ ADD_MULTIPLIED_BASE) * Π (1 + ADD_MULTIPLIED_TOTAL)}, and a rolled
 * Strength is an {@code ADD_VALUE}: it lands INSIDE the base that the phase multiplier then scales. At phase
 * 14 with Strength III that is {@code (3.0 + 9.0) * 3.30 = 39.6} raw damage — precisely the one-shot
 * threshold through un-enchanted full netherite, and exactly what a player reported. No ceiling on the curve
 * alone can close that, because the curve is the wrong factor.
 *
 * <p>So this corrects the finished value instead, with an {@code ADD_MULTIPLIED_TOTAL} modifier of
 * {@code cap / actual}. That operation is the one that composes multiplicatively at the very end, which is
 * what "take whatever this ended up as, and bring it down to here" requires; an {@code ADD_MULTIPLIED_BASE}
 * would merely add its delta to the others and land somewhere else entirely.
 *
 * <p>Enforced at spawn, not per hit. Every source of inflation this mod has — the variation roll, the phase
 * roll, the beneficial-effect draw, the special variant — is applied during {@code finalizeSpawn} and lasts
 * for the zombie's whole life, so one pass covers all of them. A temporary buff applied later by something
 * outside this mod would not be caught, which is the deliberate limit of this design.
 */
public final class AttributeCaps {
    private AttributeCaps() {}

    /**
     * Factor that brings {@code actual} down to {@code cap}, or 1.0 when nothing needs doing.
     *
     * <p>Extracted so the arithmetic can be tested without a server. A non-positive {@code actual} yields 1.0:
     * dividing by it would produce infinity or flip the sign, and an attribute that is already zero cannot be
     * over its cap anyway.
     */
    public static double capFactor(double actual, double cap) {
        if (cap <= 0.0 || actual <= 0.0 || actual <= cap) {
            return 1.0;
        }
        return cap / actual;
    }

    /** Bring every capped attribute of {@code z} within its ceiling. Safe to call more than once. */
    public static void enforce(Zombie z) {
        cap(z, Attributes.ATTACK_DAMAGE, "cap_attack_damage", ProgressionConfig.phaseDamageCap);
        cap(z, Attributes.MOVEMENT_SPEED, "cap_movement_speed", ProgressionConfig.phaseSpeedCap);
        // Health last, and re-filled afterwards: shrinking max health leaves the current value above the new
        // maximum, and vanilla renders that as a health bar that cannot be drained by the missing amount.
        if (cap(z, Attributes.MAX_HEALTH, "cap_max_health", ProgressionConfig.phaseHealthCap)) {
            z.setHealth(z.getMaxHealth());
        }
    }

    /** @return true if a correction was actually stamped */
    private static boolean cap(Zombie z, Holder<Attribute> attr, String idPath, double cap) {
        if (z.getAttribute(attr) == null) {
            return false;
        }
        double factor = capFactor(z.getAttributeValue(attr), cap);
        if (factor >= 1.0) {
            // Drop any correction from a previous pass rather than leaving a stale one: the value that
            // justified it may have been re-rolled, and a modifier keyed by this id would silently persist.
            AttributeModifiers.remove(z, attr, idPath);
            return false;
        }
        AttributeModifiers.multiplyTotal(z, attr, idPath, factor);
        return true;
    }
}
