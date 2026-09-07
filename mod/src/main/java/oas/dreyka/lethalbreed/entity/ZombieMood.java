package oas.dreyka.lethalbreed.entity;

import oas.dreyka.lethalbreed.config.domain.ZombieMoodConfig;
import oas.dreyka.lethalbreed.dimension.WorldAiContext;
import oas.dreyka.lethalbreed.entity.mood.MoodMovement;
import oas.dreyka.lethalbreed.entity.mood.MoodRegen;
import oas.dreyka.lethalbreed.entity.mood.MoodStateDispatch;
import oas.dreyka.lethalbreed.entity.mood.MoodStateDispatch.State;
import oas.dreyka.lethalbreed.entity.mood.MoodTransitions;
import oas.dreyka.lethalbreed.entity.mood.ZombieMoodSounds;
import oas.dreyka.lethalbreed.entity.mood.sleep.DaySleepCycle;
import oas.dreyka.lethalbreed.probe.DevProbe;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * Per-zombie "mood" on top of the hunt, config-gated by {@link ZombieMoodConfig}: Celebrate after a clean
 * kill, Flee below {@code fleeHealthFraction} with hysteresis against {@code regainHealthFraction}, Regen
 * while fleeing, sheltering or celebrating, and the daytime doze.
 *
 * <p>The ONLY place {@link #state} is assigned: the awake transitions live in {@link MoodTransitions}, the
 * doze in {@link DaySleepCycle}, and both hand a state back without writing one. {@link #update} runs once
 * per activation from {@code LodBucketPass}; {@code drive*} run every tick from the brain.
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

    /** True while walking to shade: the brain keeps the walk calm, LodManager waits for the sky to go. */
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
            // Mood disabled at runtime: every latched state has to be handed back, not just the doze. The
            // transitions below stop running, so a zombie caught in FLEEING would retreat until it died.
            if (state == State.SLEEPING) {
                state = sleep.wake(entity, owner, now, false);
            } else if (state != State.NORMAL) {
                state = transitions.releaseAwakeMood(entity);
            }
            return;
        }
        float max = entity.getMaxHealth();
        float frac = max <= 0.0f ? 1.0f : entity.getHealth() / max;
        LivingEntity threat = currentThreat();
        // A WOUNDED zombie flees the nearest PLAYER too, not only its last aggressor. Sleep keeps the plain
        // threat below: a silent player standing over a sleeper must not wake it.
        LivingEntity fleeThreat = ZombieMoodConfig.fleeEnabled
                ? MoodTransitions.flightThreat(entity, threat, frac) : null;

        state = transitions.celebrationExpiry(entity, now, frac, state);
        state = transitions.fleeHysteresis(entity, now, frac, fleeThreat, state);
        state = transitions.sunShelter(entity, level, frac, state);
        state = MoodStateDispatch.settle(entity, sleep.tick(level, entity, owner, now, threat, state));
        // Per-state side effects (see MoodStateDispatch): the distress scream rallies the horde only once
        // the zombie has opened ground between itself and what it flees.
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

    /** Dash to the shade from {@link #update}, or a plain retreat when none was found: standing still cooks. */
    public void driveShelter(ServerLevel level) {
        BlockPos shelter = transitions.shelterTarget();
        if (shelter != null) {
            MoodMovement.driveToShelter(entity, shelter);
            return;
        }
        driveFlee(level);
    }

    /** Hand vanilla AI back if the doze holds it, so the live entity is sane when this mood is discarded. */
    public void releaseAiHold() {
        sleep.releaseAiHold(entity);
    }

    /** True while the doze holds vanilla AI off; the save-path mixin asks before dropping NoAI (audit #2). */
    public boolean holdsAiFreeze() {
        return sleep.holdsAiFreeze();
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
