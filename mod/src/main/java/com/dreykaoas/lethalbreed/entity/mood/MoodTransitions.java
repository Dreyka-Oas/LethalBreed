package com.dreykaoas.lethalbreed.entity.mood;

import com.dreykaoas.lethalbreed.config.domain.ZombieMoodConfig;
import com.dreykaoas.lethalbreed.entity.mood.MoodStateDispatch.State;
import com.dreykaoas.lethalbreed.entity.mood.sleep.SunShelterOverride;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * The three awake mood transitions, in the order the activation runs them: the celebration latch
 * expiring, the flee hysteresis, and the sun-shelter override on top of a retreat.
 *
 * <p>Each takes the current {@link State} and returns the resulting one; none writes the caller's
 * field. The timers and the shade target they need across activations live here, next to the only
 * code that reads them.
 */
public final class MoodTransitions {

    private final FleeThreatTracker fleeTracker = new FleeThreatTracker();
    private long celebrateUntil = Long.MIN_VALUE;
    private long corneredUntil = Long.MIN_VALUE;
    /** The shade dash of a burning wounded zombie: refuge, stall watchdog and retry cooldown. */
    private final SunShelterOverride shelterDash = new SunShelterOverride();
    private boolean distressScreamed = false;

    public BlockPos shelterTarget() {
        return shelterDash.shelterTarget();
    }

    public boolean distressScreamed() {
        return distressScreamed;
    }

    public void markDistressScreamed() {
        distressScreamed = true;
    }

    /**
     * Drop whatever awake mood is latched and hand the zombie back to the hunt. Called when the mood option
     * is switched off at runtime: a zombie already inside FLEEING or SHELTERING has nothing left to take it
     * out, since the transitions above stop running, and it would keep retreating until it died.
     *
     * <p>Lives here rather than in the caller because the flee tracker is private to this class, and a
     * tracker left holding the old threat would make the next flee decide against stale distances.
     */
    public State releaseAwakeMood(Zombie entity) {
        entity.setAggressive(false);
        celebrateUntil = Long.MIN_VALUE;
        shelterDash.clearTarget();
        distressScreamed = false;
        fleeTracker.reset();
        return State.NORMAL;
    }

    /** Enter CELEBRATING: arms up for {@code celebrateTicks}. */
    public State celebrate(long now) {
        celebrateUntil = now + ZombieMoodConfig.celebrateTicks;
        return State.CELEBRATING;
    }

    /** The celebration latch expires by itself; if still hurt AND flee is on, roll into FLEEING: the
     *  healing continues where the hunt would otherwise resume. */
    public State celebrationExpiry(Zombie entity, long now, float frac, State state) {
        if (state != State.CELEBRATING || now < celebrateUntil) {
            return state;
        }
        entity.setAggressive(false);
        distressScreamed = false;
        return (ZombieMoodConfig.fleeEnabled && frac < ZombieMoodConfig.regainHealthFraction)
                ? State.FLEEING : State.NORMAL;
    }

    /**
     * Flee hysteresis, only when {@code fleeEnabled}. A wounded zombie retreats (plus the distress rally
     * scream, fired in the dispatch), enters below {@code fleeHealthFraction} and leaves at regain or when
     * cornered. Disabled at runtime, any lingering FLEEING drops straight back to the hunt so toggling the
     * option off does not strand a fleer.
     */
    public State fleeHysteresis(Zombie entity, long now, float frac, LivingEntity fleeThreat, State state) {
        if (!ZombieMoodConfig.fleeEnabled) {
            if (state == State.FLEEING) {
                distressScreamed = false;
                return State.NORMAL;
            }
            return state;
        }
        if (state == State.FLEEING) {
            var outcome = FleeHysteresis.whileFleeing(entity, fleeThreat, frac, fleeTracker);
            if (outcome.stayFleeing()) {
                return state;
            }
            distressScreamed = false;
            if (outcome.enterFight()) {
                corneredUntil = now + ZombieMoodConfig.corneredFightTicks;
            }
            return State.NORMAL;
        }
        if (state != State.CELEBRATING && FleeHysteresis.shouldEnter(now, corneredUntil, frac, fleeThreat)) {
            distressScreamed = false;
            fleeTracker.reset();
            return State.FLEEING;
        }
        return state;
    }

    /** Sun-shelter override: dash to shade instead of a straight retreat while burning in the open. */
    public State sunShelter(Zombie entity, ServerLevel level, float frac, State state) {
        boolean fleeingOrSheltering = state == State.FLEEING || state == State.SHELTERING;
        if (SunShelterOverride.eligible(fleeingOrSheltering, frac)) {
            return shelterDash.evaluate(entity, level, level.getGameTime())
                    ? State.SHELTERING : State.FLEEING;
        }
        if (state == State.SHELTERING) {
            shelterDash.clearTarget(); // no longer eligible; healed up, or the threat-gone path ran above
            return State.FLEEING;
        }
        return state;
    }

    /** What a fleeing zombie actually runs from: its recent aggressor if any, else, only while wounded,
     *  the nearest nearby player, so a wounded zombie keeps fleeing when you walk up to it instead of
     *  freezing once the aggressor memory lapses. A healthy zombie gets null and hunts. */
    public static LivingEntity flightThreat(Zombie entity, LivingEntity aggressor, float frac) {
        if (aggressor != null) {
            return aggressor;
        }
        if (frac < ZombieMoodConfig.regainHealthFraction) {
            return MoodStateDispatch.nearestTargetablePlayer(entity, ZombieMoodConfig.fleeThreatRadius);
        }
        return null;
    }
}
