package oas.dreyka.lethalbreed.entity.move.gait;

import oas.dreyka.lethalbreed.config.domain.move.LeapConfig;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * What counts as a landing on the prey, how long the cling that follows lasts, what it costs and where it
 * holds the zombie. Split off {@link Cling}, which had no room left under the file budget, and worth having
 * apart anyway: everything here is geometry, and all of it but {@link #perch} is a pure function of the
 * config that a test can pin with no entity and no world.
 */
public final class ClingMath {
    private ClingMath() {}

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
     * <p>{@link #above} is the look of the thing, but a zombie is 1.95 tall and nothing else tests the space
     * up there: prey caught in a two-block corridor would wear a zombie whose eyes are in the rock, and
     * {@code Entity.isInWall} reads exactly the eye block, so that is a point of suffocation per tick for the
     * whole bite plus a body half-sunk in the ceiling. {@link #inside} is the answer to that, mostly free by
     * construction since the victim is standing in it, though a zombie is taller than most prey, so it is
     * measured too.
     */
    public static Vec3 perch(Zombie self, LivingEntity victim) {
        AABB prey = victim.getBoundingBox();
        Vec3 top = above(prey);
        if (fits(self, top)) {
            return top;
        }
        Vec3 shared = inside(prey);
        return fits(self, shared) ? shared : null;
    }

    private static boolean fits(Zombie self, Vec3 feet) {
        AABB box = self.getBoundingBox()
                .move(feet.x - self.getX(), feet.y - self.getY(), feet.z - self.getZ());
        return self.level().noCollision(self, box);
    }
}
