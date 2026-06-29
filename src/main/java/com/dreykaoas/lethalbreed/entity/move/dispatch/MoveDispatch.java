package com.dreykaoas.lethalbreed.entity.move.dispatch;

import com.dreykaoas.lethalbreed.config.domain.CombatMoveConfig;
import com.dreykaoas.lethalbreed.config.domain.FlowConfig;
import com.dreykaoas.lethalbreed.dimension.WorldAIContext;
import com.dreykaoas.lethalbreed.entity.LODLevel;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import com.dreykaoas.lethalbreed.entity.move.Descend;
import com.dreykaoas.lethalbreed.entity.move.MoveMath;
import com.dreykaoas.lethalbreed.entity.move.Obstacle;
import com.dreykaoas.lethalbreed.entity.move.PillarClimb;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/**
 * Final movement-mode selection once the zombie has navigated toward its target: whether it has arrived
 * (melee, no block ops), or must climb to an overhead target, descend to a lower one, or break/bridge a
 * lateral obstacle. Pure decision + delegation to the leaf step units.
 */
public final class MoveDispatch {
    private MoveDispatch() {
    }

    public static void choose(SmartZombie owner, ServerLevel level, WorldAIContext ctx, PillarClimb pillar,
                              LivingEntity te, double dx, double dz, double dy, double horizSq, boolean stuck,
                              int bx, int bz) {
        // Arrived (in range + line of sight) → let vanilla melee finish it; do no block ops.
        boolean canHit = te != null && te.isAlive()
                && horizSq <= CombatMoveConfig.meleeStopRange * CombatMoveConfig.meleeStopRange
                && Math.abs(dy) <= CombatMoveConfig.meleeStopHeight
                && owner.entity().getSensing().hasLineOfSight(te);
        if (canHit) {
            return;
        }

        // HIGH/MEDIUM climb/descend to reach an elevated/lower target; LOW/FROZEN stay ground-only.
        boolean canClimbLod = owner.lod() == LODLevel.HIGH || owner.lod() == LODLevel.MEDIUM;
        if (!canClimbLod) {
            return;
        }
        double climbR = FlowConfig.climbHorizRadius;
        boolean targetOverhead = dy >= FlowConfig.climbThreshold && horizSq <= climbR * climbR;
        boolean targetUnderfoot = dy <= -CombatMoveConfig.descendThreshold && horizSq <= climbR * climbR;
        int sdx = MoveMath.stepSign(dx);
        int sdz = MoveMath.stepSign(dz);
        if (targetOverhead) {
            pillar.initiate();
        } else if (targetUnderfoot) {
            Descend.step(owner, level, ctx, sdx, sdz);
        } else if (stuck) {
            if (dy <= -CombatMoveConfig.descendThreshold) {
                Descend.step(owner, level, ctx, sdx, sdz);
            } else {
                Obstacle.handleToward(owner, level, ctx, bx, bz, sdx, sdz);
            }
        }
    }
}
