package com.dreykaoas.lethalbreed.entity.move;

import com.dreykaoas.lethalbreed.LethalBreed;
import com.dreykaoas.lethalbreed.config.domain.CombatMoveConfig;
import com.dreykaoas.lethalbreed.config.domain.FlowConfig;
import com.dreykaoas.lethalbreed.config.domain.ProgressionConfig;
import com.dreykaoas.lethalbreed.config.domain.SchedulerConfig;
import com.dreykaoas.lethalbreed.config.domain.TargetingConfig;
import com.dreykaoas.lethalbreed.dimension.WorldAIContext;
import com.dreykaoas.lethalbreed.entity.move.dispatch.MoveDispatch;
import com.dreykaoas.lethalbreed.entity.LODLevel;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import com.dreykaoas.lethalbreed.entity.ZombiePursuit;
import com.dreykaoas.lethalbreed.entity.ZombieState;
import com.dreykaoas.lethalbreed.special.SpecialBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * Per-tick orchestrator for a {@link SmartZombie}: target attack, leap, navigation, then {@link MoveDispatch}
 * picks the movement mode. Owns the transient per-tick bookkeeping + the pillar/leap state units.
 */
public final class ZombieBrain {
    private final SmartZombie owner;
    private final Zombie entity;
    private final PillarClimb pillar;
    private final Leap leap;

    private int activations;
    private int sinceNav;
    private double lastHorizDistSq = -1.0;
    private int stuckTicks = 0;
    private int dbgN = 0;
    private boolean swimming = false;

    public ZombieBrain(SmartZombie owner) {
        this.owner = owner;
        this.entity = owner.entity();
        this.pillar = new PillarClimb(owner);
        this.leap = new Leap(owner);
    }

    public boolean isClimbing() { return pillar.active(); }
    public boolean isSwimming() { return swimming; }

    /** Distance-tier throttle: true on 1 of every {@code divisor} activations of this zombie. */
    public boolean dueThisActivation(int divisor) { return divisor <= 1 || (activations++ % divisor) == 0; }

    public void tick(ServerLevel level, WorldAIContext ctx) {
        if (!owner.isValid()) return;
        ZombiePursuit p = owner.pursuit();
        int bx = entity.blockPosition().getX();
        int bz = entity.blockPosition().getZ();
        ctx.spatialGrid().update(owner, bx, bz);
        p.tickSpecial();
        if (p.isSpecialActive()) SpecialBehavior.tick(owner, level, ctx);
        if (owner.lod() == LODLevel.FROZEN) return;
        pillar.tickCooldown();
        if (pillar.active()) return; // mid jump-pillar; the scheduler's per-tick climbStep finishes it
        if (!p.hasTarget()) {
            owner.setState(p.hasSound() && navigateToSound() ? ZombieState.PURSUING_SOUND : ZombieState.IDLE);
            return;
        }

        // Actually attack the chosen target (damage animals/villagers/mobs, not just approach).
        LivingEntity te = p.targetEntity();
        if (TargetingConfig.attackAllTargets && te != null && te.isAlive() && entity.getTarget() != te) {
            entity.setTarget(te);
        }
        double dx = p.tgtX() - entity.getX();
        double dz = p.tgtZ() - entity.getZ();
        double dy = p.tgtY() - entity.getY();
        double horizSq = dx * dx + dz * dz;
        // In water never build — rise/dive is driven every tick by the scheduler's swim pass (swimStep).
        if (CombatMoveConfig.floatInWater && entity.isInWater()) {
            pillar.cancel();
            swimming = true;
            owner.setState(ZombieState.PURSUING_PLAYER);
            return;
        }
        swimming = false;
        // Occasional leap; a successful leap carries the arc this tick.
        leap.tickCooldown();
        if (leap.tryLeap(level, dx, dz, dy, horizSq)) {
            owner.setState(ZombieState.PURSUING_PLAYER);
            return;
        }

        // Block ops only when STUCK (no horizontal progress) — else it walks/auto-steps normally.
        boolean progressing = lastHorizDistSq < 0.0 || horizSq < lastHorizDistSq - 0.25;
        stuckTicks = progressing ? 0 : stuckTicks + 1;
        lastHorizDistSq = horizSq;
        boolean stuck = stuckTicks >= CombatMoveConfig.stuckActivations;

        // Aim at the BASE of an overhead target's column (our own Y) so we walk up and close the gap.
        double navY = (dy > 1.0) ? entity.getY() : p.tgtY();
        navTo(p.tgtX(), navY, p.tgtZ());
        owner.setState(ZombieState.PURSUING_PLAYER);
        debugClimb(p, horizSq, dy, stuck);
        MoveDispatch.choose(owner, level, ctx, pillar, te, dx, dz, dy, horizSq, stuck, bx, bz);
    }

    private void debugClimb(ZombiePursuit p, double horizSq, double dy, boolean stuck) {
        if (!ProgressionConfig.debugClimb || (dbgN++ % 4 != 0)) return;
        LethalBreed.LOGGER.info("[ClimbDbg] z{} y={} tgtY={} horiz={} dy={} stuck={}({}) climb={} ground={}",
                entity.getId(), MoveMath.f1(entity.getY()), MoveMath.f1(p.tgtY()),
                MoveMath.f1(Math.sqrt(horizSq)), MoveMath.f1(dy), stuck, stuckTicks, pillar.active(),
                entity.onGround());
    }

    /** Scheduler entry point each tick for an ascending zombie. Drives the jump-and-place pillar. */
    public void climbStep(ServerLevel level, WorldAIContext ctx) { pillar.step(level, ctx); }

    /** Per-tick while in water. Guards the swim state, then delegates the driving to {@link Swim}. */
    public void swimStep(ServerLevel level, WorldAIContext ctx) {
        if (!swimming) return;
        if (!owner.isValid() || !entity.isInWater()) {
            swimming = false;
            return;
        }
        pillar.cancel();
        Swim.drive(owner, level, ctx);
    }

    private boolean navigateToSound() {
        ZombiePursuit p = owner.pursuit();
        double dx = p.soundX() - entity.getX();
        double dz = p.soundZ() - entity.getZ();
        double arrive = TargetingConfig.soundArriveDistance;
        if (dx * dx + dz * dz <= arrive * arrive) {
            p.clearSound();
            return false;
        }
        navTo(p.soundX(), p.soundY(), p.soundZ());
        return true;
    }

    /** Re-path only when the previous path finished or the re-issue interval elapsed. */
    private void navTo(double x, double y, double z) {
        PathNavigation nav = entity.getNavigation();
        // Distant zombies re-path less: a stale path costs little when far, so MEDIUM/LOW stretch the
        // re-issue interval by their multiplier. nav.isDone() still re-paths immediately for any tier.
        int mult = switch (owner.lod()) {
            case MEDIUM -> Math.max(1, SchedulerConfig.lodMediumNavMultiplier);
            case LOW -> Math.max(1, SchedulerConfig.lodLowNavMultiplier);
            default -> 1;
        };
        int reissue = Math.max(1, SchedulerConfig.navReissueInterval) * mult;
        if (nav.isDone() || sinceNav >= reissue) {
            nav.moveTo(x, y, z, FlowConfig.navSpeed);
            sinceNav = 0;
        } else {
            sinceNav++;
        }
    }
}
