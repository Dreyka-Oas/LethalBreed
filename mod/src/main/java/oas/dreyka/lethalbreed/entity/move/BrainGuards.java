package oas.dreyka.lethalbreed.entity.move;

import oas.dreyka.lethalbreed.entity.move.gait.climb.PillarClimb;
import oas.dreyka.lethalbreed.dimension.WorldAiContext;
import oas.dreyka.lethalbreed.entity.SmartZombie;
import oas.dreyka.lethalbreed.entity.ZombiePursuit;
import oas.dreyka.lethalbreed.entity.ZombieState;
import oas.dreyka.lethalbreed.special.SpecialBehavior;
import oas.dreyka.lethalbreed.util.MovementLock;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * Every reason a zombie does something other than hunt, in the order {@link ZombieBrain} tests them.
 *
 * <p>Each guard answers "did I handle this tick?" and, when it did, has already put the zombie into the
 * state it belongs in. The order is the policy and it lives in the brain; what each case does lives here.
 */
final class BrainGuards {

    private final SmartZombie owner;
    private final Zombie entity;
    private final PillarClimb pillar;
    private final BrainNavigator nav;
    /** True while the swim driver owns this zombie; read back by the brain for {@code isSwimming()}. */
    private boolean swimming = false;
    /** True while {@link #handleArmed} holds the movement lock, so it is given back exactly once. */
    private boolean parked = false;

    BrainGuards(SmartZombie owner, Zombie entity, PillarClimb pillar, BrainNavigator nav) {
        this.owner = owner;
        this.entity = entity;
        this.pillar = pillar;
        this.nav = nav;
    }

    boolean swimming() {
        return swimming;
    }

    void stopSwimming() {
        swimming = false;
    }

    /** BOMBER with a lit fuse: frozen in place, swelling toward detonation. Distinct from FROZEN (which
     *  means "no target, not simulated"): an armed Bomber is very much simulated, just deliberately not
     *  moving, exactly like a Creeper mid-hiss. */
    boolean handleArmed() {
        if (!SpecialBehavior.fuseIsLit(entity)) {
            if (parked) {
                MovementLock.release(entity);
                parked = false;
            }
            return false;
        }
        pillar.cancel();
        // A Bomber that arms mid-swim must stop swimming too: EveryTickPass.processSwimmers keeps calling
        // swimStep() every server tick (outside the normal LOD-throttled cadence) for as long as
        // isSwimming() answers true, which would keep dragging it toward its target through the whole fuse.
        swimming = false;
        // Stopping the navigation alone left the Bomber creeping about a block over a long fuse: the walk
        // input MoveControl had already written stays in force, and the wander goal is free to hand out a
        // new path the moment the target below is cleared. MovementLock takes both away.
        MovementLock.hold(entity);
        parked = true;
        // Kill horizontal momentum only. Falling still falls, so an armed Bomber mid-leap lands normally
        // and does not freeze in the air.
        entity.setDeltaMovement(0.0, entity.getDeltaMovement().y, 0.0);
        // Also null the VANILLA melee target: LodManager.classify() re-asserts it every activation
        // (independently of our pursuit target below), and vanilla's own ZombieAttackGoal steers off that
        // target on every real game tick regardless of our LOD-bucketed activation cadence, same reason
        // LodBucketPass nulls it for a FROZEN zombie. Without this, an armed Bomber still creeps toward its
        // target between our activations even with navigation stopped and deltaMovement zeroed here. Fuse
        // logic no longer reads this once armed (SpecialBehavior only consults tgt while fuse <= 0), so
        // clearing it is safe.
        entity.setTarget(null);
        owner.setState(ZombieState.ARMED);
        return true;
    }

    /** Head under water: stop hunting and climb out. Ahead of the hunt because a
     *  zombie that is drowning has nothing more pressing, and behind the pursuit it would be overruled every
     *  activation by a path back toward the target on the far bank. */
    boolean handleSubmerged() {
        if (WaterFear.submerged(entity)) {
            pillar.cancel();
            WaterFear.retreat(entity, owner);
            owner.setState(ZombieState.IDLE);
            return true;
        }
        return false;
    }

    boolean handleSleeping() {
        if (!owner.mood().isSleeping()) return false;
        pillar.cancel();
        entity.getNavigation().stop();
        owner.setState(ZombieState.SLEEPING);
        return true;
    }

    boolean handleSheltering(ServerLevel level) {
        if (!owner.mood().isSheltering()) return false;
        pillar.cancel();
        owner.setState(ZombieState.SHELTERING);
        owner.mood().driveShelter(level);
        return true;
    }

    boolean handleFleeing(ServerLevel level) {
        if (!owner.mood().isFleeing()) return false;
        pillar.cancel();
        owner.setState(ZombieState.FLEEING);
        owner.mood().driveFlee(level);
        return true;
    }

    boolean handleNoTarget(WorldAiContext ctx, ZombiePursuit p) {
        if (p.hasTarget()) return false;
        owner.setState(p.hasSound() && nav.navigateToSound(ctx) ? ZombieState.PURSUING_SOUND : ZombieState.IDLE);
        return true;
    }

    boolean handleSwimEntry() {
        if (!(WaterFear.swims() && entity.isInWater()
                && (!entity.onGround() || entity.isUnderWater()))) {
            // Clearing on the way out is what the state needs, not just on the way in: a zombie that walks up
            // a shore on an activation where swimStep does not run would otherwise keep a stale flag, and
            // EveryTickPass would go on driving it as a swimmer on dry land.
            swimming = false;
            return false;
        }
        pillar.cancel();
        swimming = true;
        owner.setState(ZombieState.PURSUING_PLAYER);
        return true;
    }

}
