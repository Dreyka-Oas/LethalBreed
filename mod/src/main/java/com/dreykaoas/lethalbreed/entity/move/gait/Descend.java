package com.dreykaoas.lethalbreed.entity.move.gait;

import com.dreykaoas.lethalbreed.entity.move.MoveMath;


import com.dreykaoas.lethalbreed.config.domain.CombatMoveConfig;
import com.dreykaoas.lethalbreed.config.domain.engine.FlowConfig;

import com.dreykaoas.lethalbreed.dimension.WorldAiContext;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import com.dreykaoas.lethalbreed.entity.ZombieState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * Descend toward a lower target. Prefers to just walk: flat ground ahead is walked across, a short safe
 * drop ({@link CombatMoveConfig#safeDropBlocks}) is stepped off for free. Only when neither is possible
 * does it carve a forward STAIRCASE or build a stair over a genuine void — so it never breaks a floor it
 * could stand on, nor digs itself into an unsafe fall.
 */
public final class Descend {
    private Descend() {
    }

    public static void step(SmartZombie owner, ServerLevel level, WorldAiContext ctx, int sdx, int sdz) {
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

        // 1) Forward step toward the target's column: flat ground, a clean stair, a short safe drop, or a
        //    built support over a void. Falls through when the body space ahead is blocked by something
        //    unbreakable.
        if ((sdx != 0 || sdz != 0) && DescendForward.step(owner, level, ctx, by, ax, az)) {
            return;
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

        // 2b) Target hid straight DOWN a deep shaft (it fell/dug and is nearly right below us): the safe-carve
        //     above bailed because the shaft is deeper than safeDropBlocks, and a lateral staircase would only
        //     walk us away from a prey that is directly under our feet. Dig straight down after it anyway — one
        //     block per activation — so the zombie follows its target down the hole instead of stranding above.
        double hx = owner.tgtX() - entity.getX();
        double hz = owner.tgtZ() - entity.getZ();
        double dbr = CombatMoveConfig.descendDirectlyBelowRadius;
        boolean directlyBelow = hx * hx + hz * hz <= dbr * dbr; // within ~1.5 blocks horizontally by default
        BlockPos straightUnder = new BlockPos(bx, by - 1, bz);
        if (directlyBelow && MoveMath.breakableSolid(level, straightUnder)) {
            entity.getNavigation().stop();
            ctx.breakManager().request(straightUnder, entity);
            owner.setState(ZombieState.DESCENDING);
            return;
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
