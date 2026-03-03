/**
 * Project: Lethal Breed
 * Responsibility: AI State Machine for Building and Mining
 * License: O.A.S - MS-RSL (Microsoft Reference Source License)
 * Copyright (c) 2026 O.A.S (Optimization & Quality). All rights reserved.
 */
package oas.work.lethalbreed.ai.builder;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import oas.work.lethalbreed.mixin.EntityAccessor;
import java.util.concurrent.atomic.AtomicReference;

public class BuildStateMachine {
    private final ZombieEntity zombie;
    private int state = 0, breakTimer = 0, globalCooldown = 0, stuckTicks = 0, jumpTimer = 0;
    private BlockPos lockedBlock, basePos;
    private final AtomicReference<BlockPos> asyncBlock = new AtomicReference<>(null);
    private final AtomicReference<Boolean> thinking = new AtomicReference<>(false);
    private final TargetLogic targetLogic = new TargetLogic();

    public BuildStateMachine(ZombieEntity z) { this.zombie = z; }

    public void tick() {
        LivingEntity t = zombie.getTarget();
        if (t != null && zombie.canSee(t) && zombie.squaredDistanceTo(t.getX(), zombie.getY(), t.getZ()) < 9.0 && Math.abs(t.getY() - zombie.getY()) < 1.5) {
            if (state != 0 || targetLogic.hasSound()) reset();
        }
        if (state == 2) {
            if (lockedBlock == null || ((EntityAccessor)zombie).getWorld().getBlockState(lockedBlock).isAir()) { reset(); return; }
            if (BreakAction.tick(zombie, lockedBlock, ++breakTimer)) reset();
            return;
        }
        if (globalCooldown > 0) { globalCooldown--; return; }
        Vec3d tPos = targetLogic.getTarget(zombie, t);
        if (tPos == null) return;
        if (state == 0) {
            zombie.getLookControl().lookAt(tPos.x, tPos.y, tPos.z);
            state = ChaseLogic.process(zombie, tPos, asyncBlock, thinking, stuckTicks, this);
        } else if (state == 1) state = JumpAction.tick(zombie, basePos, ++jumpTimer);
    }

    public boolean canStart() { return BuildConditions.canStart(zombie, state) || targetLogic.hasSound(); }
    public void reset() { state = 0; basePos = null; lockedBlock = null; stuckTicks = 0; jumpTimer = 0; breakTimer = 0; targetLogic.reset(); thinking.set(false); asyncBlock.set(null); }
    public void setLockedBlock(BlockPos lb) { lockedBlock = lb; }
    public void setStuckTicks(int st) { stuckTicks = st; }
    public void setGlobalCooldown(int cd) { globalCooldown = cd; }
    public void setBasePos(BlockPos bp) { basePos = bp; }
    public void setJumpTimer(int jt) { jumpTimer = jt; }
}
