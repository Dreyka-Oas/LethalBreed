package com.dreykaoas.lethalbreed.entity.move;

import com.dreykaoas.lethalbreed.config.domain.CombatMoveConfig;
import com.dreykaoas.lethalbreed.config.domain.engine.FlowConfig;

import com.dreykaoas.lethalbreed.block.BlockOperationQueue;
import com.dreykaoas.lethalbreed.block.BreachCoordinator;
import com.dreykaoas.lethalbreed.dimension.WorldAiContext;
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

    public static void handleToward(SmartZombie owner, ServerLevel level, WorldAiContext ctx,
                                    int bx, int bz, int sdx, int sdz, BlockPos target, boolean committed) {
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
        BlockPos forwardColumn = new BlockPos(ax, y, az);
        boolean columnBreakable = false;
        for (int i = 0; i < cells && !columnBreakable; i++) {
            columnBreakable = MoveMath.breakableSolid(level, new BlockPos(ax, y + i, az));
        }
        if (columnBreakable) {
            // FOCUS FIRE: instead of each zombie chipping its own front column, converge on ONE shared breach
            // column at a time. The coordinator hands back the breach column (mine, or a neighbour's).
            BreachCoordinator.BreachTarget bt =
                    ctx.breachCoordinator().resolve(forwardColumn, entity.blockPosition(), target, committed);
            BlockPos base = bt.column();
            // Adjacent to the shared breach (mine, or I've funnelled right up to it) → hammer THAT column, so
            // the few zombies that fit around it pile on (concentration bonus). Otherwise walk to its attack
            // side first. A zombie already committed (breaking last tick) always gets its own column back and
            // so never steers — it stays put on the block it started, as it should.
            boolean withinReach = base.equals(forwardColumn) || entity.blockPosition().distSqr(base) <= 3.0;
            if (!withinReach) {
                entity.getNavigation().moveTo(bt.approach().getX() + 0.5, bt.approach().getY(),
                        bt.approach().getZ() + 0.5, FlowConfig.navSpeed);
                owner.setState(ZombieState.PURSUING_PLAYER);
                return;
            }
            boolean requested = false;
            for (int i = 0; i < cells; i++) {
                BlockPos p = base.above(i);
                if (MoveMath.breakableSolid(level, p)) {
                    ctx.breakManager().request(p, entity);
                    requested = true;
                }
            }
            if (requested) {
                owner.setState(ZombieState.BREAKING);
                return;
            }
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
