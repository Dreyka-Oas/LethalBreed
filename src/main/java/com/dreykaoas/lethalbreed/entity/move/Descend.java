package com.dreykaoas.lethalbreed.entity.move;

import com.dreykaoas.lethalbreed.config.domain.CombatMoveConfig;
import com.dreykaoas.lethalbreed.config.domain.FlowConfig;

import com.dreykaoas.lethalbreed.dimension.WorldAIContext;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import com.dreykaoas.lethalbreed.entity.ZombieState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Descend toward a lower target. Prefers to just walk: flat ground ahead is walked across, a short safe
 * drop ({@link LethalBreedConfig#safeDropBlocks}) is stepped off for free. Only when neither is possible
 * does it carve a forward STAIRCASE or build a stair over a genuine void — so it never breaks a floor it
 * could stand on, nor digs itself into an unsafe fall.
 */
public final class Descend {
    private Descend() {
    }

    public static void step(SmartZombie owner, ServerLevel level, WorldAIContext ctx, int sdx, int sdz) {
        Zombie entity = owner.entity();
        int bx = entity.blockPosition().getX();
        int by = entity.blockPosition().getY();
        int bz = entity.blockPosition().getZ();

        // 0) Cheapest descent: a nearby edge with a short SAFE drop — just step/drop off it for free.
        if (tryWalkableStepDown(owner, level, bx, by, bz)) {
            owner.setState(ZombieState.DESCENDING);
            return;
        }

        int ax = bx + sdx;
        int az = bz + sdz;

        // 1) Forward staircase toward the target's column: the cell one step down has a solid floor → take
        //    that 1-block step, breaking any breakable head/feet block in the way.
        if (sdx != 0 || sdz != 0) {
            BlockPos head = new BlockPos(ax, by, az);
            BlockPos feet = new BlockPos(ax, by - 1, az);
            BlockPos floor = new BlockPos(ax, by - 2, az);
            BlockState hd = level.getBlockState(head);
            BlockState ft = level.getBlockState(feet);
            BlockState fl = level.getBlockState(floor);
            boolean headClear = hd.isAir() || !hd.blocksMotion();
            boolean feetClear = ft.isAir() || !ft.blocksMotion();

            // Flat walkable ground ahead (solid floor + clear body): the descent is further on — walk to it,
            // never break a floor we could stand on.
            if (ft.blocksMotion() && headClear
                    && !level.getBlockState(new BlockPos(ax, by + 1, az)).blocksMotion()) {
                entity.getNavigation().moveTo(ax + 0.5, by, az + 0.5, FlowConfig.navSpeed);
                owner.setState(ZombieState.DESCENDING);
                return;
            }
            if (fl.blocksMotion()) {
                if (!headClear && MoveMath.breakableSolid(level, head)) {
                    ctx.breakManager().request(head, entity);
                    owner.setState(ZombieState.DESCENDING);
                    return;
                }
                if (!feetClear && MoveMath.breakableSolid(level, feet)) {
                    ctx.breakManager().request(feet, entity);
                    owner.setState(ZombieState.DESCENDING);
                    return;
                }
                // Clean 1-block step down with a floor → walk to it (keeps the zombie on the stair, no leap).
                entity.getNavigation().moveTo(ax + 0.5, by - 1, az + 0.5, FlowConfig.navSpeed);
                owner.setState(ZombieState.DESCENDING);
                return;
            }
            // Forward cell is a drop (no floor one step down).
            if (feetClear && headClear) {
                int fall = MoveMath.fallDistanceInto(level, ax, by, az, CombatMoveConfig.safeDropBlocks);
                if (fall <= CombatMoveConfig.safeDropBlocks) {
                    // Short safe drop → step off the edge toward the target for free.
                    entity.getNavigation().moveTo(ax + 0.5, by - fall, az + 0.5, FlowConfig.navSpeed);
                    owner.setState(ZombieState.DESCENDING);
                    return;
                }
                // Deep void TOWARD the target → BUILD a descending step in the target's direction. Place the
                // support FIRST and WAIT; only step once it exists (the block-op queue drains after the tick).
                entity.getNavigation().stop();
                BlockPos land = new BlockPos(ax, by - 2, az);
                BlockState ld = level.getBlockState(land);
                if (ld.isAir() || !ld.blocksMotion()) {
                    ctx.blockOps().enqueuePlace(land);
                    owner.setState(ZombieState.BUILDING);
                    return;
                }
                entity.getNavigation().moveTo(ax + 0.5, by - 1, az + 0.5, FlowConfig.navSpeed);
                owner.setState(ZombieState.BUILDING);
                return;
            }
            // Body space ahead blocked by an unbreakable obstacle → fall through to the straight-down carve.
        }

        // 2) Carve straight DOWN through our own floor toward a target below — one block per activation, but
        //    ONLY when the resulting fall is safe (a solid landing within safeDropBlocks under the removed
        //    block). Stop nav so the vanilla pathfinder doesn't drag it off the column.
        BlockPos under = new BlockPos(bx, by - 1, bz);
        if (MoveMath.breakableSolid(level, under)) {
            int fall = MoveMath.fallDistanceInto(level, bx, by - 1, bz, CombatMoveConfig.safeDropBlocks);
            if (fall <= CombatMoveConfig.safeDropBlocks) {
                entity.getNavigation().stop();
                ctx.breakManager().request(under, entity);
                owner.setState(ZombieState.DESCENDING);
                return;
            }
        }

        // 3) Target directly below over a deep void: can't drop straight safely. Don't strand — build a
        //    descending staircase out over the air toward the target.
        StairDescent.build(owner, level, ctx, bx, by, bz, sdx, sdz);
    }

    /**
     * Walk off an adjacent safe step-down instead of digging straight through our own floor. Scans the four
     * cardinal neighbours for a body-clear column that drops a short, safe distance; on the first hit it
     * paths there and returns true. Returns false when boxed in.
     */
    private static boolean tryWalkableStepDown(SmartZombie owner, ServerLevel level, int bx, int by, int bz) {
        Zombie entity = owner.entity();
        final int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : dirs) {
            int nx = bx + d[0];
            int nz = bz + d[1];
            if (level.getBlockState(new BlockPos(nx, by, nz)).blocksMotion()) {
                continue; // feet space into that column is blocked
            }
            if (level.getBlockState(new BlockPos(nx, by + 1, nz)).blocksMotion()) {
                continue; // head space is blocked
            }
            int fall = MoveMath.fallDistanceInto(level, nx, by, nz, CombatMoveConfig.safeDropBlocks);
            if (fall >= 1 && fall <= CombatMoveConfig.safeDropBlocks) {
                entity.getNavigation().moveTo(nx + 0.5, by - fall, nz + 0.5, FlowConfig.navSpeed);
                return true;
            }
        }
        return false;
    }
}
