package com.dreykaoas.lethalbreed.entity;

import com.dreykaoas.lethalbreed.config.domain.ProgressionConfig;
import com.dreykaoas.lethalbreed.util.AttributeModifiers;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * The last word on how hard, how tough and how fast a zombie may ever be.
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
 * <p><b>Enforced repeatedly, not once at spawn.</b> A spawn-time pass is provably insufficient, and this was
 * measured rather than reasoned about: vanilla's own zombie-leader bonus is an {@code ADD_MULTIPLIED_TOTAL}
 * of up to x5 stamped in {@code Zombie.handleAttributes}, and stamped AGAIN at runtime whenever a zombie
 * summons reinforcements. A correction placed before it is simply multiplied by it — which is how a capped
 * zombie was caught at 227 health against a 200 ceiling, with the correction still visibly attached. So this
 * also runs on every LOD activation, where the common case costs one attribute read that finds nothing to do.
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

    /**
     * Bring every capped attribute of {@code z} within its ceiling. Idempotent, and cheap when there is
     * nothing to do — which is the overwhelmingly common case once a zombie has been corrected once.
     */
    public static void enforce(Zombie z) {
        cap(z, Attributes.ATTACK_DAMAGE, "cap_attack_damage", ProgressionConfig.phaseDamageCap);
        cap(z, Attributes.MOVEMENT_SPEED, "cap_movement_speed", ProgressionConfig.phaseSpeedCap);
        // Health last, and re-filled afterwards: shrinking max health leaves the current value above the new
        // maximum, and vanilla renders that as a health bar that cannot be drained by the missing amount.
        if (cap(z, Attributes.MAX_HEALTH, "cap_max_health", ProgressionConfig.phaseHealthCap)) {
            z.setHealth(z.getMaxHealth());
        }
    }

    /**
     * Measure this attribute WITHOUT any correction of ours, then re-derive one.
     *
     * <p>The removal has to come first, and that ordering is the whole method. Reading the value while a
     * previous correction is still attached measures the capped figure, not the real one: it reports a value
     * at or under the cap, concludes nothing needs doing, drops the correction — and the attribute springs
     * back to its uncapped value. A pass that undoes the previous pass is worse than no pass at all, and it
     * fails intermittently, only on whichever zombies happen to get enforced twice.
     *
     * <p>Removing first also handles the honest case for a re-run: the roll that justified the old factor may
     * have changed, so a stale correction keyed by this id would quietly scale an attribute it no longer
     * describes.
     *
     * @return true if a correction was actually stamped
     */
    private static boolean cap(Zombie z, Holder<Attribute> attr, String idPath, double cap) {
        if (cap <= 0.0 || z.getAttribute(attr) == null || z.getAttributeValue(attr) <= cap) {
            // Already within bounds — including "within bounds BECAUSE our correction is attached", which is
            // why nothing is removed on this path. Removing here and re-deriving would read the corrected
            // value, conclude no correction is needed, and let the raw value spring straight back: a pass
            // that undoes the previous pass. Leaving early also avoids marking the attribute dirty, which
            // matters because this runs on every activation of every zombie.
            return false;
        }
        // Over the cap. Drop our own correction first so the value being measured is the real one: whatever
        // pushed it over (vanilla's leader bonus, a reinforcement bonus) has to be measured alongside
        // everything else, not on top of a factor derived before it existed.
        AttributeModifiers.remove(z, attr, idPath);
        double factor = capFactor(z.getAttributeValue(attr), cap);
        if (factor >= 1.0) {
            return false;
        }
        AttributeModifiers.multiplyTotal(z, attr, idPath, factor);
        return true;
    }
}
