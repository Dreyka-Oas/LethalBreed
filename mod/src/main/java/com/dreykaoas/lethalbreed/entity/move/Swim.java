package com.dreykaoas.lethalbreed.entity.move;

import com.dreykaoas.lethalbreed.config.domain.CombatMoveConfig;

import com.dreykaoas.lethalbreed.dimension.WorldAiContext;
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

    // Scratch pos for the every-tick surface-edge reads below (server-thread only). Avoids allocating a fresh
    // BlockPos (+ another via .above()) each swim tick. NOT used for tryBreak, which must hand an immutable
    // BlockPos to the break queue.
    private static final BlockPos.MutableBlockPos EDGE = new BlockPos.MutableBlockPos();

    /** Drive the swim. Called every tick by {@code SmartZombie.swimStep} after its guard has passed. */
    public static void drive(SmartZombie owner, ServerLevel level, WorldAiContext ctx) {
        Zombie entity = owner.entity();
        LivingEntity target = owner.targetEntity();

        // Use the target's LIVE position (the cached tgt is only refreshed on the bucket cadence, which made
        // the zombie chase a stale point and look like it swam "anywhere").
        boolean haveLive = target != null && target.isAlive();
        double txx = haveLive ? target.getX() : owner.tgtX();
        double tyy = haveLive ? target.getY() : owner.tgtY();
        double tzz = haveLive ? target.getZ() : owner.tgtZ();
        boolean targetBelow = haveLive && target.isInWater()
                && tyy < entity.getY() - CombatMoveConfig.waterSubmergeOffset;

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
        double arrive = CombatMoveConfig.waterArriveDistance;
        double desiredX = hlen > arrive ? hx / hlen * CombatMoveConfig.waterSwimSpeed : 0.0;
        double desiredZ = hlen > arrive ? hz / hlen * CombatMoveConfig.waterSwimSpeed : 0.0;
        double blend = CombatMoveConfig.waterVelocityBlend;
        double nvx = v.x * (1.0 - blend) + desiredX * blend;
        double nvz = v.z * (1.0 - blend) + desiredZ * blend;
        // Vertical: dive after a submerged target, else surface gently and hold at the top.
        double vy = targetBelow ? -CombatMoveConfig.waterDiveSpeed
                : (entity.isUnderWater() ? CombatMoveConfig.waterRiseSpeed : 0.0);
        // Step-out / stack-up: at the surface (not diving) a solid block ahead blocks horizontal progress and
        // swimming disables auto-step. Hop up when the edge is solid at foot level (climb onto shore) OR when
        // it's too tall (solid at foot+1) — repeated hops let zombies pile onto EACH OTHER (collision) and
        // scale a high bank they can't step onto alone.
        if (!targetBelow && (sdx != 0 || sdz != 0)) {
            BlockPos base = entity.blockPosition();
            int ax = base.getX() + sdx, ay = base.getY(), az = base.getZ() + sdz;
            boolean blockedFoot = level.getBlockState(EDGE.set(ax, ay, az)).isSolidRender();
            boolean tooTall = level.getBlockState(EDGE.set(ax, ay + 1, az)).isSolidRender();
            if (blockedFoot || tooTall) {
                // kept up each tick so a stalled group keeps hopping/stacking
                vy = CombatMoveConfig.waterSurfaceJump;
            }
        }

        entity.setDeltaMovement(nvx, vy, nvz);
        entity.hurtMarked = true;
        breakToward(level, ctx, entity, sdx, sdz, targetBelow);
    }

    /** Carve solid blocks between the zombie and its target while swimming (water itself isn't solid). When
     *  diving it also opens the floor cell directly below. */
    private static void breakToward(ServerLevel level, WorldAiContext ctx, Zombie entity,
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

    private static void tryBreak(ServerLevel level, WorldAiContext ctx, Zombie entity, int x, int y, int z) {
        BlockPos p = new BlockPos(x, y, z);
        if (MoveMath.breakableSolid(level, p)) {
            ctx.breakManager().request(p, entity);
        }
    }
}
