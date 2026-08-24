package com.dreykaoas.lethalbreed.entity.mood.sleep;

import com.dreykaoas.lethalbreed.entity.LodLevel;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import com.dreykaoas.lethalbreed.entity.ZombieState;
import com.dreykaoas.lethalbreed.entity.mood.MoodStateDispatch;

import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * The dozing pose itself, and the one piece of vanilla state it borrows: {@code NoAi}.
 *
 * <p>Split out of {@code ZombieMood} because the NoAi hold, alone among the state here, outlives a single
 * call and can leak: vanilla persists {@code NoAI} to entity NBT while our own flag is not persisted, so
 * a frozen zombie whose mood object dies is reloaded as a gravity-less statue. Keeping the flag and every
 * path that clears it in one small class is what makes that auditable.
 */
public final class DozePose {

    /** True while WE hold the zombie's vanilla AI off, so we only ever clear the NoAi WE set. */
    private boolean noAiFrozen = false;

    /**
     * Hold the dozing pose: drop any hunt the classify pass seeded, stop moving, sync the sleep
     * animation, and fall to FROZEN so a field of sleepers stays cheap.
     *
     * <p>NEVER freezes a mid-air zombie. {@code setNoAi(true)} makes {@code isEffectiveAi()} false, and
     * vanilla gates {@code travel()} (the ONLY path that applies gravity and consumes deltaMovement)
     * behind it, so a NoAi airborne zombie stops falling and hangs frozen forever. While airborne this
     * releases any freeze we hold and only stops pathing, so gravity lands it.
     *
     * @return true once the zombie is grounded and actually dozing; false while it is still airborne.
     */
    public boolean hold(Zombie entity, SmartZombie owner) {
        owner.cancelClimb(); // kill any in-flight leap/pillar so its impulse cannot carry the zombie
        if (!entity.onGround()) {
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
