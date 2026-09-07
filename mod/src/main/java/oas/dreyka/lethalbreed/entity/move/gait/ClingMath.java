package oas.dreyka.lethalbreed.entity.move.gait;

import oas.dreyka.lethalbreed.config.domain.move.LeapConfig;
import oas.dreyka.lethalbreed.util.AttributeModifiers;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

/**
 * What counts as a landing on the prey, how long the cling that follows lasts, what it costs, where it holds
 * the zombie and what the hold costs the prey. Split off {@link Cling}, which had no room left under the file
 * budget: everything here is a rule of the cling rather than a step of it, and all of it but {@link #perch}
 * and the grip pair is a pure function of the config that a test can pin with no entity and no world.
 */
public final class ClingMath {
    private ClingMath() {}

    /** Key of the drag a cling puts on its prey, shared by the two calls that stamp and lift it. */
    private static final String SLOW_ID = "cling_slow";

    /** How much of the distance between the two body centres a back-hug keeps. Under 1 the boxes overlap,
     *  which is what makes it read as a zombie holding on rather than one walking in single file. */
    private static final double BACK_HUG = 0.6;

    /**
     * True when a leap has put {@code self} on {@code target}: within {@code leapClingReachSq} horizontally
     * between the two box centres, feet no higher than the top of the victim and no more than
     * {@code leapClingMaxDropBelow} under its feet. Read off the real boxes, so a tall victim can be landed
     * on where a short one cannot.
     */
    public static boolean contact(AABB self, AABB target) {
        double dx = (self.minX + self.maxX - target.minX - target.maxX) * 0.5;
        double dz = (self.minZ + self.maxZ - target.minZ - target.maxZ) * 0.5;
        return dx * dx + dz * dz <= LeapConfig.leapClingReachSq
                && self.minY <= target.maxY
                && self.minY >= target.minY - LeapConfig.leapClingMaxDropBelow;
    }

    /** Game ticks a fresh cling lasts, from a roll in 0..1 across the configured band of seconds. */
    public static int durationTicks(double roll) {
        double lo = Math.min(LeapConfig.leapClingMinSeconds, LeapConfig.leapClingMaxSeconds);
        double hi = Math.max(LeapConfig.leapClingMinSeconds, LeapConfig.leapClingMaxSeconds);
        return Math.max(1, Mth.floor((lo + roll * (hi - lo)) * 20.0));
    }

    /** Health one activation of {@code activationTicks} game ticks is worth, at the configured rate. */
    public static double damageFor(int activationTicks) {
        return LeapConfig.leapClingDamage * Math.max(1, activationTicks) / 20.0;
    }

    /**
     * Feet on the victim's own floor at its back, boxes overlapping: where a zombie that caught its prey
     * hangs on.
     *
     * <p>A yaw of {@code y} points a body along {@code (-sin y, 0, cos y)}, so the back is that vector
     * negated. The reach is measured off the two bodies rather than fixed, or a zombie clinging to something
     * twice a player's width would be standing in the middle of it.
     */
    public static Vec3 behind(AABB victim, float yawDeg, double bodyDepth) {
        double reach = (Math.max(victim.getXsize(), victim.getZsize()) + bodyDepth) * 0.5 * BACK_HUG;
        double rad = Math.toRadians(yawDeg);
        return new Vec3((victim.minX + victim.maxX) * 0.5 + Math.sin(rad) * reach,
                victim.minY,
                (victim.minZ + victim.maxZ) * 0.5 - Math.cos(rad) * reach);
    }

    /** Feet on top of the victim's box, centred on it: where a passenger would have sat. */
    public static Vec3 above(AABB victim) {
        return new Vec3((victim.minX + victim.maxX) * 0.5, victim.maxY, (victim.minZ + victim.maxZ) * 0.5);
    }

    /** Feet at the victim's own feet, inside its footprint: the perch to fall back on under a low ceiling. */
    public static Vec3 inside(AABB victim) {
        return new Vec3((victim.minX + victim.maxX) * 0.5, victim.minY, (victim.minZ + victim.maxZ) * 0.5);
    }

    /**
     * Where a latched zombie goes this tick, or null when it has to let go for want of room.
     *
     * <p>{@link #behind} is the one that is meant to be seen, and it is also the roomiest: the zombie stands
     * on the prey's own floor, so anywhere the prey fits the zombie nearly fits too. {@link #above} used to
     * come first and looked wrong twice over, a zombie riding a head instead of holding a back, and a zombie
     * 1.95 tall on prey caught in a two-block corridor with its eyes in the rock. {@code Entity.isInWall}
     * reads exactly the eye block, so that was a point of suffocation per tick for the whole bite. It stays
     * as the second choice for prey backed against a wall, and {@link #inside} behind it for the rest.
     */
    public static Vec3 perch(Zombie self, LivingEntity victim) {
        AABB body = self.getBoundingBox().move(-self.getX(), -self.getY(), -self.getZ());
        return perch(victim.getBoundingBox(), body, victim.getYRot(),
                box -> self.level().noCollision(self, box));
    }

    /**
     * The choice itself, with the world behind one predicate.
     *
     * <p>Taken apart from the overload above so the four outcomes (back, head, footprint, let go) can be
     * pinned without a server: the fallbacks are the whole point of this method and a test that only reads
     * {@link #behind}, {@link #above} and {@link #inside} never visits them.
     *
     * @param body the zombie's own box measured from its feet, so ({@code 0}, {@code 0}, {@code 0}) is a
     *        zombie standing at the origin
     * @param free whether a box of that shape has the world to itself where it has been put
     */
    public static Vec3 perch(AABB prey, AABB body, float yawDeg, Predicate<AABB> free) {
        Vec3 back = behind(prey, yawDeg, Math.max(body.getXsize(), body.getZsize()));
        if (free.test(body.move(back))) {
            return back;
        }
        Vec3 top = above(prey);
        if (free.test(body.move(top))) {
            return top;
        }
        Vec3 shared = inside(prey);
        return free.test(body.move(shared)) ? shared : null;
    }

    /**
     * Put the cling's drag on the prey: it is carrying a zombie, and it moves like it.
     *
     * <p>Scaling the running total rather than the base is what makes it bite through a speed potion, and
     * transient is what makes a reload let go when nothing else is left to.
     */
    public static void grip(LivingEntity victim) {
        AttributeModifiers.multiplyTotalTransient(victim, Attributes.MOVEMENT_SPEED, SLOW_ID,
                1.0 - Mth.clamp(LeapConfig.leapClingSlowAmount, 0.0, 1.0));
    }

    /** Lift that drag. Safe on a victim that never carried one, so every exit from a cling can call it. */
    public static void ungrip(LivingEntity victim) {
        AttributeModifiers.remove(victim, Attributes.MOVEMENT_SPEED, SLOW_ID);
    }
}
