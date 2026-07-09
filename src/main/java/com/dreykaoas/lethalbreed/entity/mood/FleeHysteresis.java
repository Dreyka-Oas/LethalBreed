package com.dreykaoas.lethalbreed.entity.mood;

import com.dreykaoas.lethalbreed.config.domain.ZombieMoodConfig;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * The FLEEING entry/exit hysteresis (Schmitt trigger): entered below {@code fleeHealthFraction} while a threat
 * is around, left only once healed back to {@code regainHealthFraction}. Also owns the "cornered" give-up path
 * via the caller-supplied {@link FleeThreatTracker}.
 */
public final class FleeHysteresis {
    private FleeHysteresis() {}

    /** Outcome of one evaluation. {@code enterFight} means: stop fleeing and latch a cornered-fight cooldown. */
    public record Outcome(boolean stayFleeing, boolean enterFight) {}

    /** Already fleeing: decide whether to keep fleeing, return to NORMAL (healed), or give up (cornered). */
    public static Outcome whileFleeing(Zombie entity, LivingEntity threat, float frac, FleeThreatTracker tracker) {
        if (frac >= ZombieMoodConfig.regainHealthFraction) {
            tracker.reset();
            return new Outcome(false, false);
        }
        if (threat == null) {
            tracker.reset(); // threat gone (outran / disengaged): keep healing, drop stuck tracking
            return new Outcome(true, false);
        }
        // Cornered check: track distance to the threat across activations. If the fleer keeps failing to gain
        // ground (wall behind it, or the threat pins it in place) it abandons the retreat and turns to fight.
        tracker.track(entity.distanceToSqr(threat));
        if (tracker.shouldGiveUp(threatAtLeastAsFast(entity, threat))) {
            tracker.reset();
            return new Outcome(false, true);
        }
        return new Outcome(true, false);
    }

    /** True when the threat's movement speed is at least the fleer's effective flee speed — meaning a straight
     *  retreat can't open ground, so fleeing is futile and the fleer should give up sooner and fight. */
    private static boolean threatAtLeastAsFast(Zombie entity, LivingEntity threat) {
        double zombieBase = entity.getAttributeValue(Attributes.MOVEMENT_SPEED);
        double fleerSpeed = zombieBase * ZombieMoodConfig.fleeSpeed;
        double threatSpeed = threat.getAttributes().hasAttribute(Attributes.MOVEMENT_SPEED)
                ? threat.getAttributeValue(Attributes.MOVEMENT_SPEED)
                : zombieBase; // no speed attribute (e.g. a player uses a different model) → assume peer speed
        return threatSpeed >= fleerSpeed;
    }

    /** Not currently fleeing: true when it should start (wounded, threat present, not on cornered cooldown). */
    public static boolean shouldEnter(long now, long corneredUntil, float frac, LivingEntity threat) {
        return now >= corneredUntil && frac < ZombieMoodConfig.fleeHealthFraction && threat != null;
    }
}
