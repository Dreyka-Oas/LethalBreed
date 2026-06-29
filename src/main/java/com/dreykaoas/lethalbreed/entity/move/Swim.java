package com.dreykaoas.lethalbreed.entity.move;

import com.dreykaoas.lethalbreed.config.domain.CombatMoveConfig;

import com.dreykaoas.lethalbreed.dimension.WorldAIContext;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * Per-tick water driving. The zombie can't drown, so by default the FloatGoal keeps it bobbing at the
 * surface; when its target is itself submerged below, it dives after it — a downward impulse EVERY tick so
 * it overcomes the FloatGoal's per-tick lift. Never places blocks in water; only carves real obstacles.
 */
public final class Swim {
    private Swim() {
    }

    /** Drive the swim. Called every tick by {@code SmartZombie.swimStep} after its guard has passed. */
    public static void drive(SmartZombie owner, ServerLevel level, WorldAIContext ctx) {
        Zombie entity = owner.entity();
        LivingEntity target = owner.targetEntity();

        // Use the target's LIVE position (the cached tgt is only refreshed on the bucket cadence, which made
        // the zombie chase a stale point and look like it swam "anywhere").
        boolean haveLive = target != null && target.isAlive();
        double txx = haveLive ? target.getX() : owner.tgtX();
        double tyy = haveLive ? target.getY() : owner.tgtY();
        double tzz = haveLive ? target.getZ() : owner.tgtZ();
        boolean targetBelow = haveLive && target.isInWater() && tyy < entity.getY() - 0.5;

        // Drive the swim directly instead of via the path navigation — the water pathfinder kept failing to
        // settle and the zombie spun in circles. Stop nav, face the target, ease toward it.
        entity.getNavigation().stop();

        double hx = txx - entity.getX();
        double hz = tzz - entity.getZ();
        double hlen = Math.sqrt(hx * hx + hz * hz);
        int sdx = MoveMath.stepSign(hx);
        int sdz = MoveMath.stepSign(hz);

        MoveMath.faceHeading(entity, hx, hz);

        // Horizontal: ease toward the target (blend with current velocity so it accelerates/decelerates
        // smoothly instead of teleport-gliding at a fixed speed). Zero the drive within ~0.6 blocks.
        net.minecraft.world.phys.Vec3 v = entity.getDeltaMovement();
        double desiredX = hlen > 0.6 ? hx / hlen * CombatMoveConfig.waterSwimSpeed : 0.0;
        double desiredZ = hlen > 0.6 ? hz / hlen * CombatMoveConfig.waterSwimSpeed : 0.0;
        double nvx = v.x * 0.6 + desiredX * 0.4;
        double nvz = v.z * 0.6 + desiredZ * 0.4;
        // Vertical: dive after a submerged target, else surface gently and hold at the top.
        double vy = targetBelow ? -CombatMoveConfig.waterDiveSpeed
                : (entity.isUnderWater() ? CombatMoveConfig.waterRiseSpeed : 0.0);

        entity.setDeltaMovement(nvx, vy, nvz);
        entity.hurtMarked = true;
        breakToward(level, ctx, entity, sdx, sdz, targetBelow);
    }

    /** Carve solid blocks between the zombie and its target while swimming (water itself isn't solid). When
     *  diving it also opens the floor cell directly below. */
    private static void breakToward(ServerLevel level, WorldAIContext ctx, Zombie entity,
                                    int sdx, int sdz, boolean diving) {
        int bx = entity.blockPosition().getX();
        int by = entity.blockPosition().getY();
        int bz = entity.blockPosition().getZ();
        if (sdx != 0 || sdz != 0) {
            tryBreak(level, ctx, entity, bx + sdx, by, bz + sdz);
            tryBreak(level, ctx, entity, bx + sdx, by + 1, bz + sdz);
        }
        if (diving) {
            tryBreak(level, ctx, entity, bx, by - 1, bz);
        }
    }

    private static void tryBreak(ServerLevel level, WorldAIContext ctx, Zombie entity, int x, int y, int z) {
        BlockPos p = new BlockPos(x, y, z);
        if (MoveMath.breakableSolid(level, p)) {
            ctx.breakManager().request(p, entity);
        }
    }
}
