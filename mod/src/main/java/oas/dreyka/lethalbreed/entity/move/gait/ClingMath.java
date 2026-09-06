package oas.dreyka.lethalbreed.entity.move.gait;

import oas.dreyka.lethalbreed.config.domain.move.LeapConfig;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * What counts as a landing on the prey, how long the cling that follows lasts and what it costs. Split off
 * {@link Cling}, which had no room left under the file budget, and worth having apart anyway: these three
 * are pure functions of the config and can be pinned by a test with no entity and no world.
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
}
