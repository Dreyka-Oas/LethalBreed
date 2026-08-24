package com.dreykaoas.lethalbreed.entity.move.gait;

import com.dreykaoas.lethalbreed.config.domain.CombatMoveConfig;
import com.dreykaoas.lethalbreed.config.domain.engine.FlowConfig;
import com.dreykaoas.lethalbreed.dimension.WorldAiContext;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import com.dreykaoas.lethalbreed.entity.ZombieState;
import com.dreykaoas.lethalbreed.entity.move.MoveMath;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The forward step of a descent: what happens in the cell one step toward the target, before {@link Descend}
 * falls back to carving straight down.
 *
 * <p>Four outcomes, in order of preference: walk on flat ground, take a clean one-block stair (breaking a
 * head or feet block if one is in the way), drop off a short safe edge, or build a support out over a deep
 * void and wait for it.
 */
final class DescendForward {
    private DescendForward() {}

    /** @return true when this step handled the tick and {@link Descend} must stop. */
    static boolean step(SmartZombie owner, ServerLevel level, WorldAiContext ctx,
                        int by, int ax, int az) {
        Zombie entity = owner.entity();
        BlockPos head = new BlockPos(ax, by, az);
        BlockPos feet = new BlockPos(ax, by - 1, az);
        BlockPos floor = new BlockPos(ax, by - 2, az);
        BlockState hd = level.getBlockState(head);
        BlockState ft = level.getBlockState(feet);
        BlockState fl = level.getBlockState(floor);
        boolean headClear = hd.isAir() || !hd.blocksMotion();
        boolean feetClear = ft.isAir() || !ft.blocksMotion();

        // Flat walkable ground ahead (solid floor plus a clear body): the descent is further on, so walk to
        // it and never break a floor we could stand on. This is the only goal in this file at the zombie's own
        // level, i.e. one cell away, and that is why it needs an accuracy the others do not: see walkFlat.
        if (ft.blocksMotion() && headClear
                && !level.getBlockState(new BlockPos(ax, by + 1, az)).blocksMotion()) {
            return walkFlat(owner, entity, ax, by, az);
        }
        if (fl.blocksMotion()) {
            if (MoveMath.requestBreakBodyBlock(owner, level, ctx, head, hd, ZombieState.DESCENDING)
                    || MoveMath.requestBreakBodyBlock(owner, level, ctx, feet, ft, ZombieState.DESCENDING)) {
                return true;
            }
            // The same rule the drop branch below applies, and it has to be applied here too: a step whose
            // body space is solid and UNBREAKABLE is not a step. Without this the branch claims the tick and
            // orders a walk into the wall, so a boxed-in zombie never reaches the carve branches at all.
            // Measured in a bedrock shaft: the zombie drifted two thirds of a block off its prey's column, the
            // step signs stopped being zero, and it spent 258 of 260 ticks in DESCENDING walking into bedrock
            // with the diggable floor under its own feet untouched.
            if (!feetClear || !headClear) {
                return false;
            }
            // Clean one-block step down with a floor: walk to it, which keeps the zombie on the stair.
            return walkTo(owner, entity, ax, by - 1, az, ZombieState.DESCENDING);
        }
        if (!feetClear || !headClear) {
            return false; // body space ahead blocked by something unbreakable: let the caller carve down
        }
        int fall = MoveMath.fallDistanceInto(level, ax, by, az, CombatMoveConfig.safeDropBlocks);
        if (fall <= CombatMoveConfig.safeDropBlocks) {
            return walkTo(owner, entity, ax, by - fall, az, ZombieState.DESCENDING); // step off the edge
        }
        // Deep void TOWARD the target: BUILD a descending step in the target's direction. Place the support
        // FIRST and WAIT; only step once it exists, since the block-op queue drains after the tick.
        entity.getNavigation().stop();
        BlockPos land = new BlockPos(ax, by - 2, az);
        BlockState ld = level.getBlockState(land);
        if (ld.isAir() || !ld.blocksMotion()) {
            ctx.blockOps().enqueuePlace(land);
            owner.setState(ZombieState.BUILDING);
            return true;
        }
        return walkTo(owner, entity, ax, by - 1, az, ZombieState.BUILDING);
    }

    private static boolean walkTo(SmartZombie owner, Zombie entity, int x, int y, int z, ZombieState state) {
        entity.getNavigation().moveTo(x + 0.5, y, z + 0.5, FlowConfig.navSpeed);
        owner.setState(state);
        return true;
    }

    /**
     * The flat step, pathed with accuracy 0 instead of the 1 {@code moveTo(x, y, z, speed)} passes for us.
     *
     * <p>Accuracy is the Manhattan radius at which {@code PathFinder} calls a target reached, so at 1 any node
     * ONE block from the goal already counts, and the zombie's own node is exactly that for a goal one cell
     * ahead: the path comes back already complete, the navigation reports done, and nothing moves. Every other
     * goal in this file is at least two nodes away (a step down, a drop, a built support), which is why they
     * work at the default and this one never did. Measured on the descend rig: a zombie whose prey was three
     * blocks below and ten out held its ground for four hundred ticks, state DESCENDING the whole time,
     * having neither walked to the ledge nor dug a single block.
     */
    private static boolean walkFlat(SmartZombie owner, Zombie entity, int ax, int by, int az) {
        entity.getNavigation().moveTo(ax + 0.5, by, az + 0.5, 0, FlowConfig.navSpeed);
        owner.setState(ZombieState.DESCENDING);
        return true;
    }
}
