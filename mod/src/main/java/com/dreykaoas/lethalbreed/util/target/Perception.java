package com.dreykaoas.lethalbreed.util.target;

import com.dreykaoas.lethalbreed.config.domain.TargetingConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The two senses, kept apart from the target policy that consumes them: what a zombie can hear, and
 * what it can see.
 */
public final class Perception {
    private Perception() {}

    /**
     * An entity is audible only when it actually emits noise this tick: walking (moved at least
     * {@code soundMoveThreshold} horizontally and not sneaking), performing an action (arm swing, so
     * attack, place, break or mine, or using an item, so eat or drink), or hurt (crying on damage, being
     * hit, burning). A motionless, silent entity makes no sound and can only be acquired by line of
     * sight. Mirrors the player-footstep rule in {@code SoundEventBus.tickPlayers}, so hearing is
     * consistent for all entities.
     */
    public static boolean isAudible(LivingEntity e) {
        Vec3 v = e.getDeltaMovement();
        double hMove = Math.sqrt(v.x * v.x + v.z * v.z); // horizontal only, ignore gravity on a standing mob
        boolean walking = hMove >= TargetingConfig.soundMoveThreshold && !e.isCrouching();
        boolean acting = e.swinging || e.isUsingItem();  // place / break / mine / eat / drink
        boolean hurt = e.hurtTime > 0 || e.isOnFire();   // taking damage / being hit / burning
        return walking || acting || hurt;
    }

    /**
     * Line of sight from the zombie's eyes to the target's, treating only OPAQUE blocks as vision
     * blockers: translucent blocks (glass, ice, leaves) are see-through. Coarse voxel walk, deliberately
     * cheap, since it runs once per candidate per activation.
     */
    public static boolean canSee(ServerLevel level, Mob self, LivingEntity target) {
        Vec3 from = self.getEyePosition();
        Vec3 to = target.getEyePosition();
        Vec3 delta = to.subtract(from);
        double dist = delta.length();
        if (dist < 1.0e-3) {
            return true;
        }
        double step = 0.5;
        int steps = (int) (dist / step);
        double sx = delta.x / dist * step;
        double sy = delta.y / dist * step;
        double sz = delta.z / dist * step;
        double cx = from.x, cy = from.y, cz = from.z;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int i = 1; i < steps; i++) {
            cx += sx; cy += sy; cz += sz;
            m.set(Mth.floor(cx), Mth.floor(cy), Mth.floor(cz));
            BlockState s = level.getBlockState(m);
            if (s.canOcclude()) {
                return false; // opaque full block, sight blocked (glass, leaves and ice do not occlude)
            }
        }
        return true;
    }
}
