package oas.dreyka.lethalbreed.entity.move;

import oas.dreyka.lethalbreed.entity.move.gait.Cling;
import oas.dreyka.lethalbreed.entity.move.gait.Leap;
import oas.dreyka.lethalbreed.entity.move.gait.climb.PillarClimb;
import oas.dreyka.lethalbreed.config.domain.CombatMoveConfig;
import oas.dreyka.lethalbreed.config.domain.engine.FlowConfig;
import oas.dreyka.lethalbreed.dimension.WorldAiContext;
import oas.dreyka.lethalbreed.entity.SmartZombie;
import oas.dreyka.lethalbreed.entity.ZombiePursuit;
import oas.dreyka.lethalbreed.entity.ZombieState;
import oas.dreyka.lethalbreed.entity.move.dispatch.MoveDispatch;
import oas.dreyka.lethalbreed.probe.DevProbe;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * The hunt itself, once {@link ZombieBrain} has ruled out every reason not to hunt: leap, navigate, then
 * hand off to {@link MoveDispatch} for breaking, pillaring and forced descent.
 *
 * <p>Owns the two pieces of per-tick memory the hunt needs across ticks: how far the target was last tick
 * (the stuck detector) and whether a block break was in progress (the breaking latch). Both belong here,
 * off the brain, because nothing outside this step reads them.
 */
final class PursueStep {

    private final SmartZombie owner;
    private final Zombie entity;
    private final PillarClimb pillar;
    private final Leap leap;
    private final Cling cling;
    private final BrainNavigator nav;

    private double lastHorizDistSq = -1.0;
    private int stuckTicks = 0;
    /** Latched last tick: hold position on the block instead of re-pathing. */
    private boolean breaking = false;

    PursueStep(SmartZombie owner, Zombie entity, PillarClimb pillar, Leap leap, Cling cling,
               BrainNavigator nav) {
        this.owner = owner;
        this.entity = entity;
        this.pillar = pillar;
        this.leap = leap;
        this.cling = cling;
        this.nav = nav;
    }

    void run(ServerLevel level, WorldAiContext ctx, ZombiePursuit p, int bx, int bz) {
        // A latched zombie is being placed by Cling, not steered: navigation, block ops and the leap all
        // have to keep out of the way, and the stuck detector must not read the ride as a lack of progress.
        if (cling.active()) {
            return;
        }
        // The vanilla attack target (melee) is set authoritatively in LodManager.classify, which runs in the
        // SAME activation immediately before this tick, so no setTarget re-assert is needed here. We still
        // read the pursuit target to drive movement dispatch below.
        LivingEntity te = p.targetEntity();
        // A day-sleeper calmly walking to its shade block (a memory target, so te == null) must NOT use the
        // combat approach: leaping toward shade reads as a jerky pounce. Plain navigation only; it still digs
        // if genuinely walled in (stuck detection below), just no speculative hops.
        boolean shadeSeek = te == null && owner.mood().isSeekingShade();
        // A pack MARCHING to its rendezvous navigates and nothing else: no leap, no pillar, no breaching. A
        // migration must not tear through a base its route happens to cross; destruction is reserved for an
        // actual aggro. Vanilla pathing walks around whatever it can; when there is no way round at all the
        // pack stalls, and PackMarch gives up on that destination after packStuckActivations.
        boolean packMarch = te == null && p.pack().hasWaypoint();
        double dx = p.tgtX() - entity.getX();
        double dz = p.tgtZ() - entity.getZ();
        double dy = p.tgtY() - entity.getY();
        double horizSq = dx * dx + dz * dz;

        // Block ops only when STUCK (no horizontal progress), else it walks and auto-steps normally. Computed
        // BEFORE the leap so a stuck zombie (mid-break or mid-pillar) never leaps: a leap would move it off
        // the block it is breaking, stop renewing the break request, and let the progress lapse.
        boolean progressing = lastHorizDistSq < 0.0
                || horizSq < lastHorizDistSq - CombatMoveConfig.stuckProgressEpsilon;
        stuckTicks = progressing ? 0 : stuckTicks + 1;
        lastHorizDistSq = horizSq;
        boolean stuck = stuckTicks >= CombatMoveConfig.stuckActivations;

        // Occasional leap; a successful one carries the arc this tick. Suppressed while stuck (breaking) and
        // while calmly walking to shade for a day-doze.
        leap.tickCooldown();
        if (!stuck && !shadeSeek && !packMarch && leap.tryLeap(level, dx, dz, dy, horizSq)) {
            owner.setState(ZombieState.PURSUING_PLAYER);
            return;
        }
        // Then, on the activations that leap is still resolving over: did the arc come down on the prey?
        // Only a real entity can be ridden, so a memory target (te == null) never latches anything.
        if (cling.tryCling(te, leap.leaping())) {
            return;
        }

        if (breaking) {
            // Was breaking a block last tick, so CONCENTRATE: hold position, do not re-path. Re-pathing would
            // let the flow field drag the zombie sideways around the wall, so it stops renewing the break
            // request and the progress lapses (the block never reaches 100%). Just keep facing it.
            entity.getNavigation().stop();
            MoveMath.faceHeading(entity, dx, dz);
        } else {
            // Aim at the BASE of an overhead target's column (our own Y) so we walk up and close the gap.
            double navY = (dy > FlowConfig.navYThreshold) ? entity.getY() : p.tgtY();
            nav.navTo(ctx, p.tgtX(), navY, p.tgtZ());
        }
        owner.setState(ZombieState.PURSUING_PLAYER);
        trace(level, p, horizSq, dy, stuck);

        if (packMarch) {
            // Dispatch is the only path to block breaking, pillaring and forced descent, so skipping it is
            // what makes a migration non-destructive. Clear the latch too, so a member that aggroes mid-wall
            // and then loses its target does not resume a break it is no longer entitled to.
            breaking = false;
            return;
        }
        // Pass the current breaking latch (was I breaking last tick?) so a committed zombie stays anchored on
        // its block instead of being re-steered to another breach mid-break.
        MoveDispatch.choose(owner, level, ctx, pillar, te, dx, dz, dy, horizSq, stuck, bx, bz, breaking);
        breaking = owner.state() == ZombieState.BREAKING;
    }

    /**
     * Throttled to roughly 1-in-4: the entity id is phased against the world's tick counter so different
     * zombies log on different ticks and the per-tick volume stays bounded, without a new per-zombie field
     * and without touching the LOD cadence counter {@code LodBucketPass} drives.
     */
    private void trace(ServerLevel level, ZombiePursuit p, double horizSq, double dy, boolean stuck) {
        if (!DevProbe.tracing(DevProbe.CLIMB)
                || Math.floorMod(entity.getId() + level.getGameTime(), 4L) != 0L) {
            return;
        }
        DevProbe.sink.trace(DevProbe.CLIMB, "z" + entity.getId()
                + " pursue y=" + MoveMath.f1(entity.getY())
                + " tgt=(" + MoveMath.f1(p.tgtX()) + ", " + MoveMath.f1(p.tgtY()) + ", " + MoveMath.f1(p.tgtZ()) + ")"
                + " horiz=" + MoveMath.f1(Math.sqrt(horizSq)) + " dy=" + MoveMath.f1(dy)
                + " stuck=" + stuck + "/" + stuckTicks
                + " pillar=" + pillar.active()
                + " ground=" + entity.onGround());
    }
}
