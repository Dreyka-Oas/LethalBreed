package com.dreykaoas.lethalbreed.entity.move;

import com.dreykaoas.lethalbreed.config.domain.FlowConfig;

import com.dreykaoas.lethalbreed.dimension.WorldAIContext;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import com.dreykaoas.lethalbreed.entity.ZombieState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Build a descending staircase out over a deep void toward a lower target (the inverse of the pillar-up).
 * Lay a support one level down-and-forward, then step onto it, repeating into a safe diagonal stair down to
 * ANY depth even over open air. Auto-removed by the tracker. Used as the last resort when neither a safe
 * drop nor a carve-down is possible.
 */
final class StairDescent {
    private StairDescent() {
    }

    static void build(SmartZombie owner, ServerLevel level, WorldAIContext ctx,
                      int bx, int by, int bz, int sdx, int sdz) {
        Zombie entity = owner.entity();
        int dirx = sdx;
        int dirz = sdz;
        if (dirx == 0 && dirz == 0) {
            // Target is directly below us → pick the dominant horizontal axis toward it to stair along.
            if (Math.abs(owner.tgtX() - entity.getX()) >= Math.abs(owner.tgtZ() - entity.getZ())) {
                dirx = (owner.tgtX() >= entity.getX()) ? 1 : -1;
            } else {
                dirz = (owner.tgtZ() >= entity.getZ()) ? 1 : -1;
            }
        }
        int sx = bx + dirx;
        int sz = bz + dirz;
        BlockPos sHead = new BlockPos(sx, by, sz);
        BlockPos sFeet = new BlockPos(sx, by - 1, sz);
        BlockPos sLand = new BlockPos(sx, by - 2, sz); // one step below our current floor (by-1)
        BlockState sHd = level.getBlockState(sHead);
        BlockState sFt = level.getBlockState(sFeet);
        BlockState sLd = level.getBlockState(sLand);
        // Clear the body space for the step if a breakable block blocks it.
        if (sHd.blocksMotion() && MoveMath.breakableSolid(level, sHead)) {
            ctx.breakManager().request(sHead, entity);
            owner.setState(ZombieState.DESCENDING);
            return;
        }
        if (sFt.blocksMotion() && MoveMath.breakableSolid(level, sFeet)) {
            ctx.breakManager().request(sFeet, entity);
            owner.setState(ZombieState.DESCENDING);
            return;
        }
        boolean headClear = sHd.isAir() || !sHd.blocksMotion();
        boolean feetClear = sFt.isAir() || !sFt.blocksMotion();
        if (headClear && feetClear) {
            // Stop pursuit nav so it can't walk us off the ledge into the void while we build the step.
            entity.getNavigation().stop();
            if (sLd.isAir() || !sLd.blocksMotion()) {
                // No floor at the lower step yet → lay it FIRST and WAIT (queue drains after the tick).
                ctx.blockOps().enqueuePlace(sLand);
                owner.setState(ZombieState.BUILDING);
                return;
            }
            // The step now has a floor → walk down onto it.
            entity.getNavigation().moveTo(sx + 0.5, by - 1, sz + 0.5, FlowConfig.navSpeed);
            owner.setState(ZombieState.BUILDING);
        }
    }
}
