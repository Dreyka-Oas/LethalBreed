package com.dreykaoas.lethalbreed.entity.mood;

import com.dreykaoas.lethalbreed.config.domain.ZombieMoodConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;

/** Movement helpers for {@code ZombieMood}'s per-tick drive methods: retreating away from a threat, and
 *  dashing to a shaded refuge. Pure navigation calls on the given zombie, no shared state. */
public final class MoodMovement {
    private MoodMovement() {}

    /** Path directly away from the current threat. Uses vanilla navigation, so the fleer freely climbs/descends
     *  terrain on the way out. No-op (stop + hold position) once the threat is gone. */
    public static void driveFlee(Zombie entity, LivingEntity threat) {
        if (threat == null) {
            entity.getNavigation().stop(); // outran the threat → hold position and lick wounds while healing
            return;
        }
        Vec3 away = entity.position().subtract(threat.position());
        if (away.horizontalDistanceSqr() < 1.0e-4) {
            away = new Vec3(1.0, 0.0, 0.0); // degenerate (directly overlapping) → arbitrary direction
        }
        away = away.normalize().scale(ZombieMoodConfig.fleeDistance);
        entity.getNavigation().moveTo(entity.getX() + away.x, entity.getY(), entity.getZ() + away.z,
                ZombieMoodConfig.fleeSpeed);
        entity.getLookControl().setLookAt(entity.getX() + away.x, entity.getEyeY(), entity.getZ() + away.z);
    }

    /** Path to the shaded refuge. */
    public static void driveToShelter(Zombie entity, BlockPos shelterTarget) {
        entity.getNavigation().moveTo(shelterTarget.getX() + 0.5, shelterTarget.getY(),
                shelterTarget.getZ() + 0.5, ZombieMoodConfig.shelterSpeed);
        entity.getLookControl().setLookAt(shelterTarget.getX() + 0.5,
                shelterTarget.getY() + 0.5, shelterTarget.getZ() + 0.5);
    }
}
