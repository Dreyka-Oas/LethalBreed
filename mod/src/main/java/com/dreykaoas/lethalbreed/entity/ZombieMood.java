package com.dreykaoas.lethalbreed.entity;

import com.dreykaoas.lethalbreed.config.domain.ZombieMoodConfig;
import com.dreykaoas.lethalbreed.dimension.WorldAIContext;
import com.dreykaoas.lethalbreed.entity.mood.FleeHysteresis;
import com.dreykaoas.lethalbreed.entity.mood.FleeThreatTracker;
import com.dreykaoas.lethalbreed.entity.mood.MoodMovement;
import com.dreykaoas.lethalbreed.entity.mood.MoodRegen;
import com.dreykaoas.lethalbreed.entity.mood.MoodStateDispatch;
import com.dreykaoas.lethalbreed.entity.mood.MoodStateDispatch.State;
import com.dreykaoas.lethalbreed.entity.mood.SunShelterOverride;
import com.dreykaoas.lethalbreed.entity.mood.ZombieMoodSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * Per-zombie "mood" on top of the hunt, config-gated by {@link ZombieMoodConfig}: Celebrate (arms up + groan
 * after a clean kill), Flee (retreat + rally scream below {@code fleeHealthFraction}, hysteresis vs.
 * {@code regainHealthFraction}), Regen (self-heal while fleeing/sheltering/celebrating). Logic lives across
 * {@code com.dreykaoas.lethalbreed.entity.mood}; this class is the orchestrator + facade. {@link #update} runs
 * once per activation from {@code LodBucketPass}; the {@code drive*} methods run every tick from the brain.
 */
public final class ZombieMood {
    /** Dev instrumentation: incremented each time a fleer fires its distress scream + rally emit. */
    public static final java.util.concurrent.atomic.AtomicInteger DISTRESS_COUNT =
            new java.util.concurrent.atomic.AtomicInteger();

    private final Zombie entity;
    private final SmartZombie owner;

    private State state = State.NORMAL;
    private long celebrateUntil = Long.MIN_VALUE;
    private long lastRegenTime = 0L;
    private boolean distressScreamed = false;
    private final FleeThreatTracker fleeTracker = new FleeThreatTracker();
    private long corneredUntil = Long.MIN_VALUE;
    // Sun-shelter target: the shaded block a burning wounded zombie is dashing to, if one was found.
    private BlockPos shelterTarget = null;

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

    /** Once-per-activation mood step: state transitions, distress scream, and self-heal. */
    public void update(ServerLevel level, WorldAIContext ctx) {
        if (!ZombieMoodConfig.moodEnabled || !entity.isAlive()) {
            return;
        }
        long now = level.getGameTime();
        float max = entity.getMaxHealth();
        float frac = max <= 0.0f ? 1.0f : entity.getHealth() / max;
        LivingEntity threat = currentThreat();

        // Celebration latch expires on its own; if still hurt, roll into FLEEING to keep healing.
        if (state == State.CELEBRATING && now >= celebrateUntil) {
            entity.setAggressive(false);
            state = frac < ZombieMoodConfig.regainHealthFraction ? State.FLEEING : State.NORMAL;
            distressScreamed = false;
        }

        // Flee hysteresis (see FleeHysteresis): enter below fleeHealthFraction while threatened, leave at regain.
        if (state == State.FLEEING) {
            var outcome = FleeHysteresis.whileFleeing(entity, threat, frac, fleeTracker);
            if (!outcome.stayFleeing()) {
                state = State.NORMAL;
                distressScreamed = false;
                if (outcome.enterFight()) {
                    corneredUntil = now + ZombieMoodConfig.corneredFightTicks;
                }
            }
        } else if (state != State.CELEBRATING && FleeHysteresis.shouldEnter(now, corneredUntil, frac, threat)) {
            state = State.FLEEING;
            distressScreamed = false;
            lastRegenTime = now;
            fleeTracker.reset();
        }

        // Sun-shelter override: dashes to shade instead of a straight retreat while burning in the open.
        boolean fleeingOrSheltering = state == State.FLEEING || state == State.SHELTERING;
        if (SunShelterOverride.eligible(fleeingOrSheltering, frac)) {
            var res = SunShelterOverride.evaluate(entity, level, shelterTarget);
            shelterTarget = res.shelterTarget();
            state = res.sheltering() ? State.SHELTERING : State.FLEEING;
        } else if (state == State.SHELTERING) {
            shelterTarget = null; // no longer eligible (healed up / threat gone path handled above)
            state = State.FLEEING;
        }

        // Per-state side effects (see MoodStateDispatch): drop the hunt, keep LOD alive, fire distress scream.
        boolean screamed = MoodStateDispatch.apply(state, entity, level, owner, ctx, threat, distressScreamed);
        if (screamed) {
            distressScreamed = true;
            DISTRESS_COUNT.incrementAndGet();
        }

        // Self-heal while fleeing, sheltering, or celebrating and still hurt.
        boolean regenEligible = state != State.NORMAL && frac < ZombieMoodConfig.regainHealthFraction;
        lastRegenTime = MoodRegen.tick(entity, regenEligible, now, lastRegenTime);
    }

    /** Drive the retreat: path away from the current threat (see {@link MoodMovement}). Called each tick from
     *  the brain while fleeing. */
    public void driveFlee(ServerLevel level) {
        MoodMovement.driveFlee(entity, currentThreat());
    }

    /** Drive the dash to shade found in {@link #update}. Falls back to a plain retreat when no shade was located
     *  (so a burning zombie in the open still moves rather than standing still and cooking). */
    public void driveShelter(ServerLevel level) {
        if (shelterTarget != null) {
            MoodMovement.driveToShelter(entity, shelterTarget);
            return;
        }
        driveFlee(level); // no shade nearby — keep retreating from the threat rather than stalling in the sun
    }

    /** A direct kill with no other prey within {@code celebrateRadius}: celebrate (arms up + a loud groan). */
    public void tryCelebrate(ServerLevel level) {
        if (!ZombieMoodConfig.moodEnabled || !entity.isAlive()) {
            return;
        }
        if (!MoodStateDispatch.preyCleared(entity, level, ZombieMoodConfig.celebrateRadius)) {
            return; // another valid target still lurks: the kill did NOT clear the area
        }
        state = State.CELEBRATING;
        celebrateUntil = level.getGameTime() + ZombieMoodConfig.celebrateTicks;
        lastRegenTime = level.getGameTime();
        entity.setAggressive(true); // raises the zombie's arms client-side
        owner.setState(ZombieState.CELEBRATING);
        ZombieMoodSounds.scream(entity, level, ZombieMoodConfig.screamVolume, ZombieMoodConfig.victoryPitch);
    }

    private LivingEntity currentThreat() {
        return MoodStateDispatch.currentThreat(entity, ZombieMoodConfig.fleeThreatRadius);
    }
}
