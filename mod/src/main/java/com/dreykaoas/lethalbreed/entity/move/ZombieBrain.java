package com.dreykaoas.lethalbreed.entity.move;


import com.dreykaoas.lethalbreed.entity.move.gait.Leap;
import com.dreykaoas.lethalbreed.entity.move.gait.PillarClimb;
import com.dreykaoas.lethalbreed.entity.move.gait.Swim;
import com.dreykaoas.lethalbreed.dimension.WorldAiContext;
import com.dreykaoas.lethalbreed.entity.LodLevel;
import com.dreykaoas.lethalbreed.special.SpecialBehavior;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import com.dreykaoas.lethalbreed.entity.ZombiePursuit;
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

    private final BrainGuards guards;
    private final PursueStep pursue;

    private int activations;

    public ZombieBrain(SmartZombie owner) {
        this.owner = owner;
        this.entity = owner.entity();
        this.pillar = new PillarClimb(owner);
        this.leap = new Leap(owner);
        this.nav = new BrainNavigator(owner);
        this.guards = new BrainGuards(owner, this.entity, this.pillar, this.nav);
        this.pursue = new PursueStep(owner, this.entity, this.pillar, this.leap, this.nav);
    }

    public boolean isClimbing() { return pillar.active(); }
    public boolean isSwimming() { return guards.swimming(); }

    /** Force any in-progress jump-pillar off, used when a day-sleeper dozes so a half-built climb can't leave
     *  it floating (the climb drain evicts it as soon as {@link #isClimbing()} goes false). */
    public void cancelClimb() { pillar.cancel(); }

    /** Distance-tier throttle: true on 1 of every {@code divisor} activations of this zombie. */
    public boolean dueThisActivation(int divisor) { return divisor <= 1 || (activations++ % divisor) == 0; }

    public void tick(ServerLevel level, WorldAiContext ctx) {
        if (!owner.isValid()) return;
        ZombiePursuit p = owner.pursuit();
        int bx = entity.blockPosition().getX();
        int bz = entity.blockPosition().getZ();
        // No spatialGrid().update() here: LodBucketPass already refreshed this zombie's grid cell THIS same
        // activation (before the FROZEN/throttle skips) and it hasn't moved since: tick() is only reached from
        // that pass, so repeating the update is pure redundant work. bx/bz are kept for MoveDispatch below.
        p.tickSpecial();
        if (p.isSpecialActive()) SpecialBehavior.tick(owner, level, ctx);
        if (owner.lod() == LodLevel.FROZEN) return;
        // Armed Bomber: fuse is lit, so it stops dead and swells in place until the explosion, checked
        // before sleep/shelter/flee since none of those should ever interrupt a committed detonation.
        if (guards.handleArmed()) return;
        // Daytime sleep: a dozing zombie holds still. It is normally FROZEN (so this isn't even reached); this is
        // a defensive stop in case it is momentarily active. The walk-to-shade is NOT here. That's a normal
        // memory-target pursuit (NORMAL state) so the full breaking/pillaring nav carries it to the shade.
        if (guards.handleSleeping()) return;
        // Sun-shelter overrides even the retreat: a burning wounded zombie dashes to shade (mood already found
        // the refuge and dropped the target). Checked before flee so shade-seeking wins over the straight run.
        if (guards.handleSheltering(level)) return;
        // Low-health retreat overrides the hunt: the mood step already dropped the target; here we just steer
        // away from the threat (vanilla nav, so climb/descend still work). No leap/dig/dispatch while fleeing.
        if (guards.handleFleeing(level)) return;
        pillar.tickCooldown();
        // Water outranks an ascent in progress. The mid-climb return below skips every guard under it, so
        // without testing the entry here a zombie whose pit floods keeps stacking blocks until the pillar
        // ends on its own. The guard cancels the column itself, and swimStep takes over from the next tick.
        if (pillar.active()) {
            guards.handleSwimEntry();
            return; // mid climb; the per-tick climbStep finishes it
        }
        if (guards.handleNoTarget(ctx, p)) return;
        // Water entry sits here, after the no-target guard and before the hunt: a zombie in water is driven by
        // Swim from EveryTickPass, every tick, instead of the LOD-throttled walk. Tested after handleNoTarget on
        // purpose, so a targetless zombie drifting in a pond cannot latch into the swim state with nothing to
        // chase. Lost between commit 8a0f04a and this line, which left Swim.drive unreachable.
        if (guards.handleSwimEntry()) return;

        pursue.run(level, ctx, p, bx, bz);
    }

    /** Scheduler entry point each tick for an ascending zombie. Drives the active ascent, the jump-and-place
     *  pillar (places blocks under itself, so it always stands on what it builds). */
    public void climbStep(ServerLevel level, WorldAiContext ctx) {
        pillar.step(level, ctx);
    }

    /** Per-tick while in water. Guards the swim state, then delegates the driving to {@link Swim}. */
    public void swimStep(ServerLevel level, WorldAiContext ctx) {
        if (!guards.swimming()) return;
        if (!owner.isValid() || !entity.isInWater()) {
            guards.stopSwimming();
            return;
        }
        pillar.cancel();
        Swim.drive(owner, level, ctx);
    }
}
