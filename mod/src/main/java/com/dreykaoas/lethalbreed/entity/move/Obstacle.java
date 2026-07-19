package com.dreykaoas.lethalbreed.entity.move;

import com.dreykaoas.lethalbreed.config.domain.CombatMoveConfig;

import com.dreykaoas.lethalbreed.block.BlockOperationQueue;
import com.dreykaoas.lethalbreed.dimension.WorldAIContext;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import com.dreykaoas.lethalbreed.entity.ZombieState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.state.BlockState;

/** Break a breakable block, or bridge a true gap, directly ahead toward (sdx,sdz) when stuck on flat ground. */
public final class Obstacle {
    private Obstacle() {
    }

    public static void handleToward(SmartZombie owner, ServerLevel level, WorldAIContext ctx,
                                    int bx, int bz, int sdx, int sdz) {
        if (sdx == 0 && sdz == 0) {
            return;
        }
        Zombie entity = owner.entity();
        int y = entity.blockPosition().getY();
        int ax = bx + sdx;
        int az = bz + sdz;
        BlockOperationQueue ops = ctx.blockOps();

        // Clear as many vertical cells as the zombie actually occupies (size-aware, ceil of its height): a
        // 3-tall zombie needs all 3 cells ahead gone to fit through. Request EVERY breakable cell of the column
        // at once (BreakManager runs concurrent breaks) instead of one-per-activation feet-up — clearing the
        // bottom first let the zombie shuffle forward before the head cell (y+2) was done, leaving its head
        // stuck in the wall so it never passed. Breaking the whole column together opens a hole it fits through.
        int cells = MoveMath.breakHeight(entity);
        boolean requested = false;
        for (int i = 0; i < cells; i++) {
            BlockPos p = new BlockPos(ax, y + i, az);
            if (MoveMath.breakableSolid(level, p)) {
                ctx.breakManager().request(p, entity);
                requested = true;
            }
        }
        if (requested) {
            owner.setState(ZombieState.BREAKING);
            return;
        }
        BlockState fs = level.getBlockState(new BlockPos(ax, y, az));
        if (!fs.blocksMotion()) {
            // A short, walkable ledge ahead is not a pit — let the zombie step/drop down it for free instead
            // of filling it with dirt. Only bridge a true gap with no nearby landing.
            if (MoveMath.fallDistanceInto(level, ax, y, az, CombatMoveConfig.safeDropBlocks)
                    <= CombatMoveConfig.safeDropBlocks) {
                return;
            }
            BlockPos ground = new BlockPos(ax, y - 1, az);
            BlockState gs = level.getBlockState(ground);
            if (gs.isAir() || !gs.blocksMotion()) {
                ops.enqueuePlace(ground);
                owner.setState(ZombieState.BUILDING);
            }
        }
    }
}
