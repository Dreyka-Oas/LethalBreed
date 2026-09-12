package oas.dreyka.lethalbreed.entity.mood.sleep;

import oas.dreyka.lethalbreed.entity.LodLevel;
import oas.dreyka.lethalbreed.entity.SmartZombie;
import oas.dreyka.lethalbreed.entity.ZombieState;
import oas.dreyka.lethalbreed.entity.mood.MoodStateDispatch;

import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * The dozing pose itself, and the one piece of vanilla state it borrows: {@code NoAi}.
 *
 * <p>Apart from {@code ZombieMood} because the NoAi hold, alone among the state here, outlives a single
 * call and can leak: vanilla persists {@code NoAI} to entity NBT while our own flag is not persisted, so
 * a frozen zombie whose mood object dies is reloaded as a gravity-less statue. Keeping the flag and every
 * path that clears it in one small class is what makes that auditable.
 */
public final class DozePose {

    /** How far down {@link #supported} looks for something solid. Small enough that a zombie standing on
     *  the ground is always inside it, large enough to survive the usual floating-point sag. */
    private static final double SUPPORT_PROBE = 0.05;

    /** True while WE hold the zombie's vanilla AI off, so we only ever clear the NoAi WE set. */
    private boolean noAiFrozen = false;

    /**
     * Hold the dozing pose: drop any hunt the classify pass seeded, stop moving, sync the sleep
     * animation, and fall to FROZEN so a field of sleepers stays cheap.
     *
     * <p>NEVER freezes a zombie with nothing under it. {@code setNoAi(true)} makes {@code isEffectiveAi()}
     * false, and vanilla gates {@code travel()} (the ONLY path that applies gravity and consumes
     * deltaMovement) behind it, so a NoAi zombie in the air stops falling and hangs frozen forever. With
     * nothing underneath this releases any freeze we hold and only stops pathing, so gravity lands it.
     *
     * @return true once the zombie is supported and actually dozing; false while it has nothing to stand on.
     */
    public boolean hold(Zombie entity, SmartZombie owner) {
        owner.cancelClimb(); // kill any in-flight leap/pillar so its impulse cannot carry the zombie
        if (!supported(entity)) {
            release(entity);
            entity.getNavigation().stop();
            return false;
        }
        MoodStateDispatch.dropHunt(entity, owner);
        entity.getNavigation().stop();
        if (!noAiFrozen) {
            entity.setNoAi(true);
            noAiFrozen = true;
        }
        owner.setState(ZombieState.SLEEPING);
        owner.setLod(LodLevel.FROZEN);
        return true;
    }

    /**
     * Whether anything is holding the zombie up.
     *
     * <p>Deliberately not {@code onGround()}. That flag is written by the movement step, and the movement
     * step is the very thing our freeze switches off, so from the moment we take the pose it keeps answering
     * with the value it had before. Mine the floor out from under a sleeper and the guard above, asked in
     * those terms, would go on agreeing the zombie is standing on something; it hung in the air for good.
     *
     * <p>A shallow box query rather than the block below the feet: it gets a sleeper on a slab, a fence post
     * or the lip of a block right, where a single block lookup reads air and drops a freeze that was fine.
     */
    public static boolean supported(Zombie entity) {
        return !entity.level().noCollision(entity, entity.getBoundingBox().move(0.0, -SUPPORT_PROBE, 0.0));
    }

    /** True while the freeze in force is ours. The save path asks before it decides whether it may drop
     *  {@code NoAI} from the written NBT: a freeze somebody else set stays written. */
    public boolean holding() {
        return noAiFrozen;
    }

    /**
     * Hand vanilla AI back if WE are holding it, touching nothing else. Called on wake, whenever the
     * mood step notices the zombie is no longer sleeping, and when the mood object is discarded on
     * chunk unload or server stop. Idempotent.
     */
    public void release(Zombie entity) {
        if (noAiFrozen) {
            entity.setNoAi(false);
            noAiFrozen = false;
        }
    }
}
