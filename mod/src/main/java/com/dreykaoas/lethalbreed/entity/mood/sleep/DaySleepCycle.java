package com.dreykaoas.lethalbreed.entity.mood.sleep;

import com.dreykaoas.lethalbreed.config.domain.ZombieMoodConfig;
import com.dreykaoas.lethalbreed.entity.LodLevel;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import com.dreykaoas.lethalbreed.entity.mood.MoodStateDispatch.State;
import com.dreykaoas.lethalbreed.phase.PhaseManager;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * Daytime dozing: a targetless, peaceful zombie by day either dozes (in shade, or once the horde is
 * sun-immune) or, while it still burns under open sky, heads for the nearest shade first.
 *
 * <p>It never writes the caller's state field: every entry point takes the current {@link State} and
 * returns the resulting one, so {@code ZombieMood} stays the single place the mood state is assigned.
 * Runs once per activation even while FROZEN, so a dozing zombie keeps checking whether to wake.
 */
public final class DaySleepCycle {

    private final DozePose pose = new DozePose();
    private final ShadeSeek shade = new ShadeSeek();
    private final WakeSignal signal = new WakeSignal();

    public boolean seekingShade() {
        return shade.seeking();
    }

    public void releaseAiHold(Zombie entity) {
        pose.release(entity);
    }

    /** One activation. Returns the resulting mood state. */
    public State tick(ServerLevel level, Zombie entity, SmartZombie owner, long now,
                      LivingEntity threat, State state) {
        if (!ZombieMoodConfig.daySleepEnabled) {
            return state == State.SLEEPING ? wake(entity, owner, now, false) : state;
        }
        // Sleep timers and the NoAi hold are valid ONLY while actually SLEEPING. If another subsystem moved
        // us out of it (a hit forcing FLEEING through the hysteresis, bypassing wake), scrub the stale
        // fields so the next doze starts clean: a leftover reaction delay would instantly re-wake it.
        if (state != State.SLEEPING) {
            clearTimers(entity);
        }
        if (state != State.NORMAL && state != State.SLEEPING) {
            return state; // busy fleeing, sheltering or celebrating, so no dozing
        }
        boolean day = level.isBrightOutside();
        int phase = PhaseManager.current();
        // Sun-fire is deliberately NOT a disturbance: below the immunity phase an exposed zombie is ALWAYS on
        // fire, and reaching shade is exactly how it escapes that. Only a mob or NON-fire damage disturbs.
        boolean disturbed = threat != null || (entity.hurtTime > 0 && !entity.isOnFire());

        if (state == State.SLEEPING) {
            return dozing(entity, owner, now, day, disturbed, phase);
        }
        if (roused(entity, owner, now, day, disturbed, phase)) {
            return state;
        }
        // Idle daytime sleeper: shelter first if it would burn under open sky, then doze.
        if (dozeIfNotExposed(level, entity, owner, phase)) {
            return entity.onGround() ? State.SLEEPING : state;
            // Only commit to SLEEPING once grounded and frozen; if it is still finishing a leap or fall arc
            // the pose deferred, so stay put and retry next activation.
        }
        shade.seek(level, entity, owner, now);
        return state;
    }

    private State dozing(Zombie entity, SmartZombie owner, long now, boolean day, boolean disturbed, int phase) {
        if (!day || disturbed || DaySleep.staysAwake(entity, phase)) {
            return wake(entity, owner, now, false); // night fell, hit, or promoted to the awake minority
        }
        if (signal.readyToWake(now)) {
            return wake(entity, owner, now, true); // reaction delay elapsed, head to the noise it heard
        }
        doze(entity, owner);
        return State.SLEEPING;
    }

    private boolean roused(Zombie entity, SmartZombie owner, long now, boolean day, boolean disturbed, int phase) {
        if (disturbed) {
            signal.rouse(now); // a hit keeps it awake to fight back
        }
        boolean alert = signal.alert(now);
        // A pack march has the exact signature of a noise investigation, a target point with no entity behind
        // it, so without excluding it a migrating pack would never doze again and would burn under the open
        // sky for every phase below sunImmunePhase. Day-sleep wins over migration by design.
        boolean investigatingNoise = owner.hasTarget() && owner.targetEntity() == null && !shade.seeking()
                && !owner.pursuit().pack().hasWaypoint();
        if (!day || disturbed || alert || investigatingNoise || DaySleep.staysAwake(entity, phase)) {
            shade.stop(); // busy hunting or investigating, so abandon any shade-seek
            return true;
        }
        return false;
    }

    private boolean dozeIfNotExposed(ServerLevel level, Zombie entity, SmartZombie owner, int phase) {
        if (DaySleep.burnsInSun(phase) && level.canSeeSky(entity.blockPosition())) {
            return false;
        }
        // In shade, or the horde is sun-immune. Snuff any residual sun-fire from the shade-run so it is not
        // "asleep in the shade yet still on fire".
        if (shade.seeking() && entity.getRemainingFireTicks() > 0 && !level.canSeeSky(entity.blockPosition())) {
            entity.setRemainingFireTicks(0);
        }
        doze(entity, owner);
        return true;
    }

    private void doze(Zombie entity, SmartZombie owner) {
        if (pose.hold(entity, owner)) {
            shade.stop();
        }
    }

    /** Leave SLEEPING. When {@code investigate}, seed short-term memory with the last heard noise so the
     *  normal hunt walks the zombie over to check it out. */
    public State wake(Zombie entity, SmartZombie owner, long now, boolean investigate) {
        signal.rouse(now); // just roused, stay awake and hunt a while
        if (investigate) {
            signal.investigateLastNoise(owner, now);
        }
        clearTimers(entity);
        owner.setLod(LodLevel.HIGH); // re-activate immediately so it starts moving this activation
        return State.NORMAL;
    }

    /** Reset the wake timers and release any NoAi we held. Deliberately does NOT touch the shade-seek:
     *  that flag tracks an in-progress walk across NORMAL activations and is cleared where it belongs. */
    private void clearTimers(Zombie entity) {
        signal.clear();
        pose.release(entity);
    }

    /**
     * Called by the sound bus for every zombie within earshot of a noise. It re-arms the alert timer so the
     * zombie stays awake and hunts by sight and sound; for an already-awake one that is all it does. For a
     * SLEEPING one it also stashes the source and starts the reaction delay. Continuous noise keeps
     * re-arming the alert, so a chased zombie never lapses back into a doze.
     */
    public void notifyHeardSound(long now, double x, double y, double z, State state) {
        signal.rouse(now);
        if (state == State.SLEEPING) {
            signal.heard(now, x, y, z);
        }
    }
}
