package com.dreykaoas.lethalbreed.entity.move;


import com.dreykaoas.lethalbreed.LethalBreed;
import com.dreykaoas.lethalbreed.config.domain.CombatMoveConfig;
import com.dreykaoas.lethalbreed.config.domain.engine.FlowConfig;
import com.dreykaoas.lethalbreed.config.domain.engine.DevTestConfig;
import net.minecraft.world.entity.LivingEntity;
import com.dreykaoas.lethalbreed.dimension.WorldAIContext;
import com.dreykaoas.lethalbreed.entity.move.dispatch.MoveDispatch;
import com.dreykaoas.lethalbreed.entity.LODLevel;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import com.dreykaoas.lethalbreed.entity.ZombiePursuit;
import com.dreykaoas.lethalbreed.entity.ZombieState;
import com.dreykaoas.lethalbreed.special.SpecialBehavior;
import net.minecraft.server.level.ServerLevel;
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
    private final BrainNavigator nav;

    private int activations;
    private double lastHorizDistSq = -1.0;
    private int stuckTicks = 0;
    private int dbgN = 0;
    private boolean swimming = false;
    private boolean breaking = false; // latched last tick: hold position on the block instead of re-pathing

    public ZombieBrain(SmartZombie owner) {
        this.owner = owner;
        this.entity = owner.entity();
        this.pillar = new PillarClimb(owner);
        this.leap = new Leap(owner);
        this.nav = new BrainNavigator(owner);
    }

    public boolean isClimbing() { return pillar.active(); }
    public boolean isSwimming() { return swimming; }

    /** Force any in-progress jump-pillar off — used when a day-sleeper dozes so a half-built climb can't leave
     *  it floating (the climb drain evicts it as soon as {@link #isClimbing()} goes false). */
    public void cancelClimb() { pillar.cancel(); }

    /** Distance-tier throttle: true on 1 of every {@code divisor} activations of this zombie. */
    public boolean dueThisActivation(int divisor) { return divisor <= 1 || (activations++ % divisor) == 0; }

    public void tick(ServerLevel level, WorldAIContext ctx) {
        if (!owner.isValid()) return;
        ZombiePursuit p = owner.pursuit();
        int bx = entity.blockPosition().getX();
        int bz = entity.blockPosition().getZ();
        // No spatialGrid().update() here: LodBucketPass already refreshed this zombie's grid cell THIS same
        // activation (before the FROZEN/throttle skips) and it hasn't moved since — tick() is only reached from
        // that pass, so repeating the update is pure redundant work. bx/bz are kept for MoveDispatch below.
        p.tickSpecial();
        if (p.isSpecialActive()) SpecialBehavior.tick(owner, level, ctx);
        if (owner.lod() == LODLevel.FROZEN) return;
        // Daytime sleep: a dozing zombie holds still. It is normally FROZEN (so this isn't even reached); this is
        // a defensive stop in case it is momentarily active. The walk-to-shade is NOT here — that's a normal
        // memory-target pursuit (NORMAL state) so the full breaking/pillaring nav carries it to the shade.
        if (owner.mood().isSleeping()) {
            pillar.cancel();
            entity.getNavigation().stop();
            owner.setState(ZombieState.SLEEPING);
            return;
        }
        // Sun-shelter overrides even the retreat: a burning wounded zombie dashes to shade (mood already found
        // the refuge and dropped the target). Checked before flee so shade-seeking wins over the straight run.
        if (owner.mood().isSheltering()) {
            pillar.cancel();
            owner.setState(ZombieState.SHELTERING);
            owner.mood().driveShelter(level);
            return;
        }
        // Low-health retreat overrides the hunt: the mood step already dropped the target; here we just steer
        // away from the threat (vanilla nav, so climb/descend still work). No leap/dig/dispatch while fleeing.
        if (owner.mood().isFleeing()) {
            pillar.cancel();
            owner.setState(ZombieState.FLEEING);
            owner.mood().driveFlee(level);
            return;
        }
        pillar.tickCooldown();
        if (pillar.active()) return; // mid climb; the per-tick climbStep finishes it
        if (!p.hasTarget()) {
            owner.setState(p.hasSound() && nav.navigateToSound(ctx) ? ZombieState.PURSUING_SOUND : ZombieState.IDLE);
            return;
        }

        // The vanilla attack target (melee) is set authoritatively in LODManager.classify, which runs in the
        // SAME activation immediately before this tick — so no setTarget re-assert is needed here (was
        // duplicate work). We still read the pursuit target to drive movement dispatch below.
        LivingEntity te = p.targetEntity();
        // A day-sleeper calmly walking to its shade block (a memory target, so te == null) must NOT use the
        // combat approach — leaping toward shade reads as a jerky pounce. Plain navigation only; it still digs
        // if genuinely walled in (stuck-detection below), just no speculative hops.
        boolean shadeSeek = te == null && owner.mood().isSeekingShade();
        double dx = p.tgtX() - entity.getX();
        double dz = p.tgtZ() - entity.getZ();
        double dy = p.tgtY() - entity.getY();
        double horizSq = dx * dx + dz * dz;
        // Swim mode only when actually floating/submerged (off the ground, or head underwater). A shallow
        // puddle the zombie is STANDING in (on ground, head clear) must NOT lock it into swim — it still needs
        // to pillar/jump out, so we fall through to the normal dispatch below. Deep water → swimStep drives it.
        if (CombatMoveConfig.floatInWater && entity.isInWater()
                && (!entity.onGround() || entity.isUnderWater())) {
            pillar.cancel();
            swimming = true;
            owner.setState(ZombieState.PURSUING_PLAYER);
            return;
        }
        swimming = false;

        // Block ops only when STUCK (no horizontal progress) — else it walks/auto-steps normally. Computed
        // BEFORE the leap so a stuck zombie (mid-break/pillar) never leaps: a leap would move it off the block
        // it's breaking, stop renewing the break request, and let the progress lapse (never reaching 100%).
        boolean progressing = lastHorizDistSq < 0.0 || horizSq < lastHorizDistSq - CombatMoveConfig.stuckProgressEpsilon;
        stuckTicks = progressing ? 0 : stuckTicks + 1;
        lastHorizDistSq = horizSq;
        boolean stuck = stuckTicks >= CombatMoveConfig.stuckActivations;

        // Occasional leap; a successful leap carries the arc this tick. Suppressed while stuck (breaking) and
        // while calmly walking to shade for a day-doze (no pouncing at a shady spot).
        leap.tickCooldown();
        if (!stuck && !shadeSeek && leap.tryLeap(level, dx, dz, dy, horizSq)) {
            owner.setState(ZombieState.PURSUING_PLAYER);
            return;
        }

        if (breaking) {
            // Was breaking a block last tick — CONCENTRATE: hold position, don't re-path. Re-pathing would let
            // the flow field drag the zombie sideways around the wall, so it stops renewing the break request
            // and the progress lapses (block never reaches 100%). Just keep facing the block/target.
            entity.getNavigation().stop();
            MoveMath.faceHeading(entity, dx, dz);
        } else {
            // Aim at the BASE of an overhead target's column (our own Y) so we walk up and close the gap.
            double navY = (dy > FlowConfig.navYThreshold) ? entity.getY() : p.tgtY();
            nav.navTo(ctx, p.tgtX(), navY, p.tgtZ());
        }
        owner.setState(ZombieState.PURSUING_PLAYER);
        debugClimb(p, horizSq, dy, stuck);
        // Pass the current breaking-latch (was I breaking last tick?) so a committed zombie stays anchored on
        // its block instead of being re-steered to another breach mid-break.
        MoveDispatch.choose(owner, level, ctx, pillar, te, dx, dz, dy, horizSq, stuck, bx, bz, breaking);
        // Latch for next tick: MoveDispatch sets BREAKING when it requested a block break this tick.
        breaking = owner.state() == ZombieState.BREAKING;
    }

    private void debugClimb(ZombiePursuit p, double horizSq, double dy, boolean stuck) {
        if (!DevTestConfig.debugClimb || (dbgN++ % 4 != 0)) return;
        LethalBreed.LOGGER.info("[ClimbDbg] z{} y={} tgtY={} horiz={} dy={} stuck={}({}) climb={} ground={}",
                entity.getId(), MoveMath.f1(entity.getY()), MoveMath.f1(p.tgtY()),
                MoveMath.f1(Math.sqrt(horizSq)), MoveMath.f1(dy), stuck, stuckTicks, pillar.active(),
                entity.onGround());
    }

    /** Scheduler entry point each tick for an ascending zombie. Drives the active ascent — the jump-and-place
     *  pillar (places blocks under itself, so it always stands on what it builds). */
    public void climbStep(ServerLevel level, WorldAIContext ctx) {
        pillar.step(level, ctx);
    }

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
}
