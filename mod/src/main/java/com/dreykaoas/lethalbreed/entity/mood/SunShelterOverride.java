package com.dreykaoas.lethalbreed.entity.mood;

import com.dreykaoas.lethalbreed.config.domain.ZombieMoodConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * Sun-shelter override: a wounded zombie (already fleeing/sheltering) that is burning under open sky breaks off
 * the straight retreat and dashes to the nearest shade — burning to death while running in a line is worse than
 * a short detour into cover. Symmetric exit: once it's no longer on fire (or reached a shaded block) it drops
 * back to the plain FLEEING recover state.
 */
public final class SunShelterOverride {
    private SunShelterOverride() {}

    /** Result of one evaluation: the (possibly updated) shelter target, and whether SHELTERING should be active. */
    public record Result(BlockPos shelterTarget, boolean sheltering) {}

    public static Result evaluate(Zombie entity, ServerLevel level, BlockPos currentTarget) {
        boolean exposed = entity.isOnFire() && level.canSeeSky(entity.blockPosition());
        if (exposed) {
            BlockPos target = currentTarget;
            if (target == null || level.canSeeSky(target)) {
                // (re)acquire a shaded refuge; may stay null if none near
                target = ShelterFinder.findShade(level, entity.blockPosition(), ZombieMoodConfig.shelterSearchRadius);
            }
            return new Result(target, true);
        }
        return new Result(null, false); // safe now (in shade or fire out) — caller resumes plain FLEEING
    }

    /** Whether the caller is currently eligible to be re-evaluated for shelter: the master toggle is on, and
     *  the zombie is fleeing/sheltering and wounded below {@code fleeHealthFraction}. Small guard extracted so
     *  callers don't recompute the same checks.
     *
     *  <p>{@link ZombieMoodConfig#sunShelterEnabled} is honoured HERE rather than inside
     *  {@link #evaluate}: returning false from the eligibility guard makes the caller take its existing
     *  "not eligible" branch, which clears any stale shelter target and drops SHELTERING back to FLEEING. So
     *  with the toggle off a burning zombie keeps its straight retreat and simply burns — no shade search runs
     *  at all, and no zombie can be left stranded in the SHELTERING state. */
    public static boolean eligible(boolean fleeingOrSheltering, float healthFraction) {
        if (!ZombieMoodConfig.sunShelterEnabled) {
            return false;
        }
        return fleeingOrSheltering && healthFraction < ZombieMoodConfig.fleeHealthFraction;
    }
}
