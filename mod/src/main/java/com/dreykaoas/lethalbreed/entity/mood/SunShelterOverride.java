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

    /** Whether the caller is currently eligible to be re-evaluated for shelter: fleeing/sheltering and wounded
     *  below {@code fleeHealthFraction}. Small guard extracted so callers don't recompute the same two checks. */
    public static boolean eligible(boolean fleeingOrSheltering, float healthFraction) {
        return fleeingOrSheltering && healthFraction < ZombieMoodConfig.fleeHealthFraction;
    }
}
