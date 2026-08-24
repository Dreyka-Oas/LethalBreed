package com.dreykaoas.lethalbreed.entity.mood.sleep;

import com.dreykaoas.lethalbreed.config.domain.ZombieMoodConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * Sun-shelter override: a wounded zombie (already fleeing/sheltering) that is burning under open sky breaks off
 * the straight retreat and dashes to the nearest shade: burning to death while running in a line is worse than
 * a short detour into cover. Symmetric exit: once it's no longer on fire (or reached a shaded block) it drops
 * back to the plain FLEEING recover state.
 *
 * <p>One instance per zombie, held by {@code MoodTransitions}: the refuge, its stall watchdog and the retry
 * cooldown are per-zombie state and belong next to the only code that reads them.
 */
public final class SunShelterOverride {

    /** Same patience {@code ShadeSeek} gives its own walk, and for the same failure: a refuge that stops
     *  getting closer is one the zombie will never reach, and three seconds is longer than a re-path or a
     *  block break and far shorter than the burn that kills it. */
    private static final int STALL_PATIENCE = 60;

    private final ShadeStall stall = new ShadeStall(STALL_PATIENCE);
    private BlockPos shelterTarget = null;
    private long retryAt = Long.MIN_VALUE;

    /** The shaded block the zombie is dashing to, or null: {@code driveShelter} falls back to a plain retreat. */
    public BlockPos shelterTarget() {
        return shelterTarget;
    }

    /** Forget the refuge. Called when the override stops applying, and when the mood is released wholesale. */
    public void clearTarget() {
        shelterTarget = null;
        stall.reset();
    }

    /**
     * One activation of the override.
     *
     * @return true while SHELTERING applies; false hands the caller back to the plain retreat
     */
    public boolean evaluate(Zombie entity, ServerLevel level, long now) {
        if (!entity.isOnFire() || !level.canSeeSky(entity.blockPosition())) {
            clearTarget(); // safe now (in shade or fire out), caller resumes plain FLEEING
            return false;
        }
        // A refuge that stops getting closer is a death sentence, not a plan: the shade rig measured a burning
        // probe frozen at one spot with its distance to cover stuck at 12.81 for 160 ticks. Dropping the target
        // puts driveShelter back on driveFlee, which at least moves it, and the cooldown keeps the next sweep
        // from re-acquiring the same unreachable block on the very next activation.
        if (shelterTarget != null && stall.stalled(now, entity.distanceToSqr(
                shelterTarget.getX() + 0.5, shelterTarget.getY(), shelterTarget.getZ() + 0.5))) {
            clearTarget();
            retryAt = now + ZombieMoodConfig.shelterRetryTicks;
        }
        if (shelterTarget == null || level.canSeeSky(shelterTarget)) {
            shelterTarget = now < retryAt ? null
                    : ShelterFinder.findShade(level, entity.blockPosition(), ZombieMoodConfig.shelterSearchRadius);
            stall.reset(); // a fresh target gets its full patience
        }
        return true;
    }

    /** Whether the caller is currently eligible to be re-evaluated for shelter: the master toggle is on, and
     *  the zombie is fleeing/sheltering and wounded below {@code fleeHealthFraction}. Small guard extracted so
     *  callers don't recompute the same checks.
     *
     *  <p>{@link ZombieMoodConfig#sunShelterEnabled} is honoured HERE rather than inside
     *  {@link #evaluate}: returning false from the eligibility guard makes the caller take its existing
     *  "not eligible" branch, which clears any stale shelter target and drops SHELTERING back to FLEEING. So
     *  with the toggle off a burning zombie keeps its straight retreat and simply burns. No shade search runs
     *  at all, and no zombie can be left stranded in the SHELTERING state. */
    public static boolean eligible(boolean fleeingOrSheltering, float healthFraction) {
        if (!ZombieMoodConfig.sunShelterEnabled) {
            return false;
        }
        return fleeingOrSheltering && healthFraction < ZombieMoodConfig.fleeHealthFraction;
    }
}
