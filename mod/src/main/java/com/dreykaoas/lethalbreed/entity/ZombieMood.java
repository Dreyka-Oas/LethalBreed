package com.dreykaoas.lethalbreed.entity;

import com.dreykaoas.lethalbreed.config.domain.ZombieMoodConfig;
import com.dreykaoas.lethalbreed.dimension.WorldAiContext;
import com.dreykaoas.lethalbreed.entity.mood.MoodMovement;
import com.dreykaoas.lethalbreed.entity.mood.MoodRegen;
import com.dreykaoas.lethalbreed.entity.mood.MoodStateDispatch;
import com.dreykaoas.lethalbreed.entity.mood.MoodStateDispatch.State;
import com.dreykaoas.lethalbreed.entity.mood.MoodTransitions;
import com.dreykaoas.lethalbreed.entity.mood.ZombieMoodSounds;
import com.dreykaoas.lethalbreed.entity.mood.sleep.DaySleepCycle;
import com.dreykaoas.lethalbreed.probe.DevProbe;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * Per-zombie "mood" on top of the hunt, config-gated by {@link ZombieMoodConfig}: Celebrate (arms up and
 * a groan after a clean kill), Flee (retreat and rally scream below {@code fleeHealthFraction},
 * hysteresis against {@code regainHealthFraction}), Regen (self-heal while fleeing, sheltering or
 * celebrating), and the daytime doze.
 *
 * <p>The ONLY place {@link #state} is assigned: the awake transitions live in {@link MoodTransitions},
 * the doze in {@link DaySleepCycle}, and both hand a state back without writing one. {@link #update}
 * runs once per activation from {@code LodBucketPass}; {@code drive*} run every tick from the brain.
 */
public final class ZombieMood {
    private final Zombie entity;
    private final SmartZombie owner;
    private final MoodTransitions transitions = new MoodTransitions();
    private final DaySleepCycle sleep = new DaySleepCycle();

    private State state = State.NORMAL;
    private long lastRegenTime = 0L;

    public ZombieMood(Zombie entity, SmartZombie owner) {
        this.entity = entity;
        this.owner = owner;
    }

    public boolean isFleeing() {
        return state == State.FLEEING;
    }

    public boolean isSheltering() {
        return state == State.SHELTERING;
    }

    /** Dozing, or shuffling to shade before dozing: the brain holds and drives it, not the hunt. */
    public boolean isSleeping() {
        return state == State.SLEEPING;
    }

    /** True while walking to a shade block for a day-doze. Read by the brain (keeps the walk calm, no
     *  leaping) and by LodManager (a seek only "arrives" once the feet are out of the sky). */
    public boolean isSeekingShade() {
        return sleep.seekingShade();
    }

    /** Once-per-activation mood step: state transitions, distress scream, and self-heal. */
    public void update(ServerLevel level, WorldAiContext ctx) {
        if (!entity.isAlive()) {
            return;
        }
        long now = level.getGameTime();
        if (!ZombieMoodConfig.moodEnabled) {
            // Mood disabled at runtime: do not leave a zombie frozen mid-doze, hand it back to the hunt.
            if (state == State.SLEEPING) {
                state = sleep.wake(entity, owner, now, false);
            }
            return;
        }
        float max = entity.getMaxHealth();
        float frac = max <= 0.0f ? 1.0f : entity.getHealth() / max;
        LivingEntity threat = currentThreat();
        // A WOUNDED zombie flees the nearest nearby PLAYER too, not only whatever last hit it. Sleep
        // disturbance below still uses the plain threat: a silent nearby player must NOT wake a sleeper.
        LivingEntity fleeThreat = ZombieMoodConfig.fleeEnabled
                ? MoodTransitions.flightThreat(entity, threat, frac) : null;

        state = transitions.celebrationExpiry(entity, now, frac, state);
        state = transitions.fleeHysteresis(entity, now, frac, fleeThreat, state);
        state = transitions.sunShelter(entity, level, frac, state);
        // Daytime sleep runs only when not busy fleeing, sheltering or celebrating.
        state = sleep.tick(level, entity, owner, now, threat, state);

        // Per-state side effects (see MoodStateDispatch): drop the hunt, keep LOD alive, fire the distress
        // scream. The scream measures distance from what it flees, so it rallies once it has opened ground.
        if (MoodStateDispatch.apply(state, entity, level, owner, ctx, fleeThreat, transitions.distressScreamed())) {
            transitions.markDistressScreamed();
            if (DevProbe.on()) {
                DevProbe.sink.count(DevProbe.DISTRESS, DevProbe.GLOBAL);
            }
        }

        boolean regenEligible = state != State.NORMAL && frac < ZombieMoodConfig.regainHealthFraction;
        lastRegenTime = MoodRegen.tick(entity, regenEligible, now, lastRegenTime);
    }

    /** Path away from the flee threat. Called each tick from the brain while fleeing. */
    public void driveFlee(ServerLevel level) {
        float max = entity.getMaxHealth();
        float frac = max <= 0.0f ? 1.0f : entity.getHealth() / max;
        MoodMovement.driveFlee(entity, MoodTransitions.flightThreat(entity, currentThreat(), frac));
    }

    /** Drive the dash to the shade found in {@link #update}. Falls back to a plain retreat when no shade
     *  was located, so a burning zombie keeps moving. Standing still would only let it cook. */
    public void driveShelter(ServerLevel level) {
        BlockPos shelter = transitions.shelterTarget();
        if (shelter != null) {
            MoodMovement.driveToShelter(entity, shelter);
            return;
        }
        driveFlee(level);
    }

    /** Hand vanilla AI back if the doze is holding it. Called when this mood object is about to be
     *  discarded, on chunk unload or server stop: vanilla persists {@code NoAI} to entity NBT while our
     *  own flag is not, so a frozen zombie whose mood dies would reload as a statue (audit #2). */
    public void releaseAiHold() {
        sleep.releaseAiHold(entity);
    }

    /** Called by the sound bus for every zombie within earshot of a noise. */
    public void notifyHeardSound(long now, double x, double y, double z) {
        sleep.notifyHeardSound(now, x, y, z, state);
    }

    /** A direct kill with no other prey within {@code celebrateRadius}: arms up and a loud groan. */
    public void tryCelebrate(ServerLevel level) {
        if (!ZombieMoodConfig.moodEnabled || !entity.isAlive()) {
            return;
        }
        if (!MoodStateDispatch.preyCleared(entity, level, ZombieMoodConfig.celebrateRadius)) {
            return; // another valid target still lurks: the kill did NOT clear the area
        }
        state = transitions.celebrate(level.getGameTime());
        lastRegenTime = level.getGameTime();
        entity.setAggressive(true); // raises the zombie's arms client-side
        owner.setState(ZombieState.CELEBRATING);
        ZombieMoodSounds.scream(entity, level, ZombieMoodConfig.screamVolume, ZombieMoodConfig.victoryPitch);
    }

    private LivingEntity currentThreat() {
        return MoodStateDispatch.currentThreat(entity, ZombieMoodConfig.fleeThreatRadius);
    }
}
