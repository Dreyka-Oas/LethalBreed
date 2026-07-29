package com.dreykaoas.lethalbreed.entity;

import com.dreykaoas.lethalbreed.config.domain.TargetingConfig;
import com.dreykaoas.lethalbreed.config.domain.ZombieMoodConfig;
import com.dreykaoas.lethalbreed.dimension.WorldAIContext;
import com.dreykaoas.lethalbreed.entity.mood.DaySleep;
import com.dreykaoas.lethalbreed.entity.mood.FleeHysteresis;
import com.dreykaoas.lethalbreed.entity.mood.FleeThreatTracker;
import com.dreykaoas.lethalbreed.entity.mood.MoodMovement;
import com.dreykaoas.lethalbreed.entity.mood.MoodRegen;
import com.dreykaoas.lethalbreed.entity.mood.MoodStateDispatch;
import com.dreykaoas.lethalbreed.entity.mood.MoodStateDispatch.State;
import com.dreykaoas.lethalbreed.entity.mood.ShelterFinder;
import com.dreykaoas.lethalbreed.entity.mood.SunShelterOverride;
import com.dreykaoas.lethalbreed.entity.mood.ZombieMoodSounds;
import com.dreykaoas.lethalbreed.phase.PhaseManager;

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

    // ---- Daytime sleep ----
    // A pre-immunity sleeper heads for shade (pursued as a memory target, so the breaking nav gets it there) then
    // dozes; once immune it dozes in place. sleepSeekingShade marks that the current memory target IS that shade
    // (vs a heard-noise investigation), so arriving under cover flips it to a doze. A dozing zombie is held FROZEN.
    private boolean sleepSeekingShade = false;
    // Server tick before which a fresh shade search is pointless, plus where we were when it last failed.
    // findShade sweeps 8112 positions and the failing case is the one that repeats — with no memory of the
    // failure a stationary zombie rescanned the identical volume at 1 Hz forever. Normally self-limiting
    // (an exposed zombie burns and dies in ~20s), except in water: `exposed` does not test water while
    // sun-burn is blocked by isInWaterOrRain, so that zombie loops unbounded (audit #6).
    private long shadeRetryAt = Long.MIN_VALUE;
    private BlockPos shadeFailedAt = null;
    // True while WE hold the zombie's vanilla AI off (setNoAi) so a dozing zombie stays perfectly still instead
    // of being walked around by vanilla RandomStrollGoal. Tracked so we only ever clear the NoAi WE set.
    private boolean noAiFrozen = false;
    // While now < alertUntil the zombie is ROUSED: it hunts by sight AND sound and never dozes. Armed (and
    // re-armed) by hearing a noise or taking a hit; a merely-seen silent player never arms it. This timer — not
    // a flickery per-tick audibility test — is what decides day sleep-vs-hunt, so a chaser doesn't stutter.
    private long alertUntil = Long.MIN_VALUE;
    // A heard noise wakes a sleeper after a short reaction delay; wakeAt is when it completes (MIN = not waking).
    // The noise position is stashed so the woken zombie investigates the exact spot via short-term memory.
    private long wakeAt = Long.MIN_VALUE;
    private double wakeX, wakeY, wakeZ;
    private boolean hasWakePos = false;

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

    /** Dozing (or shuffling to shade before dozing) during the day — the brain holds/drives it, not the hunt. */
    public boolean isSleeping() {
        return state == State.SLEEPING;
    }

    /** True while walking to a shade block for a day-doze (the current memory target IS that shade). Read by the
     *  brain (to keep the walk calm — no leaping) and by LODManager (a shade-seek only "arrives" once the
     *  zombie's own feet are out of the sky, not merely near the shade column). */
    public boolean isSeekingShade() {
        return sleepSeekingShade;
    }

    /** Once-per-activation mood step: state transitions, distress scream, and self-heal. */
    public void update(ServerLevel level, WorldAIContext ctx) {
        if (!entity.isAlive()) {
            return;
        }
        if (!ZombieMoodConfig.moodEnabled) {
            // Mood disabled at runtime: don't leave a zombie frozen mid-doze — hand it back to the normal hunt.
            if (state == State.SLEEPING) {
                wake(level.getGameTime(), false);
            }
            return;
        }
        long now = level.getGameTime();
        float max = entity.getMaxHealth();
        float frac = max <= 0.0f ? 1.0f : entity.getHealth() / max;
        LivingEntity threat = currentThreat();
        // Flee threat (only when the flee behaviour is on): a WOUNDED zombie flees the nearest nearby PLAYER too,
        // not only whatever last hit it. Sleep-disturbance below still uses the plain `threat` (a silent nearby
        // player must NOT wake a sleeper).
        LivingEntity fleeThreat = ZombieMoodConfig.fleeEnabled ? flightThreat(threat, frac) : null;

        // Celebration latch expires on its own; if still hurt AND flee is on, roll into FLEEING to keep healing.
        if (state == State.CELEBRATING && now >= celebrateUntil) {
            entity.setAggressive(false);
            state = (ZombieMoodConfig.fleeEnabled && frac < ZombieMoodConfig.regainHealthFraction)
                    ? State.FLEEING : State.NORMAL;
            distressScreamed = false;
        }

        // Flee hysteresis — ONLY when fleeEnabled. Wounded zombie retreats (+ distress rally scream in apply),
        // enters below fleeHealthFraction, leaves at regain or when cornered. Disabled → it never flees; drop any
        // lingering FLEEING straight back to the hunt (so toggling it off at runtime doesn't strand a fleer).
        if (ZombieMoodConfig.fleeEnabled) {
            if (state == State.FLEEING) {
                var outcome = FleeHysteresis.whileFleeing(entity, fleeThreat, frac, fleeTracker);
                if (!outcome.stayFleeing()) {
                    state = State.NORMAL;
                    distressScreamed = false;
                    if (outcome.enterFight()) {
                        corneredUntil = now + ZombieMoodConfig.corneredFightTicks;
                    }
                }
            } else if (state != State.CELEBRATING
                    && FleeHysteresis.shouldEnter(now, corneredUntil, frac, fleeThreat)) {
                state = State.FLEEING;
                distressScreamed = false;
                lastRegenTime = now;
                fleeTracker.reset();
            }
        } else if (state == State.FLEEING) {
            state = State.NORMAL;
            distressScreamed = false;
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

        // Daytime sleep (runs only when NOT busy fleeing/sheltering/celebrating): a targetless zombie dozes by
        // day; below the immunity phase it first shuffles to shade. Owns the SLEEPING state + wake handling.
        handleDaySleep(level, now, threat);

        // Per-state side effects (see MoodStateDispatch): drop the hunt, keep LOD alive, fire distress scream.
        // The scream measures distance from what it's fleeing (fleeThreat), so it rallies once it's opened ground.
        boolean screamed = MoodStateDispatch.apply(state, entity, level, owner, ctx, fleeThreat, distressScreamed);
        if (screamed) {
            distressScreamed = true;
            DISTRESS_COUNT.incrementAndGet();
        }

        // Self-heal while fleeing, sheltering, or celebrating and still hurt.
        boolean regenEligible = state != State.NORMAL && frac < ZombieMoodConfig.regainHealthFraction;
        lastRegenTime = MoodRegen.tick(entity, regenEligible, now, lastRegenTime);
    }

    /** Drive the retreat: path away from the flee threat (recent aggressor, or the nearest player while wounded).
     *  Called each tick from the brain while fleeing. */
    public void driveFlee(ServerLevel level) {
        float max = entity.getMaxHealth();
        float frac = max <= 0.0f ? 1.0f : entity.getHealth() / max;
        MoodMovement.driveFlee(entity, flightThreat(currentThreat(), frac));
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

    /** Daytime dozing state machine. A targetless, peaceful zombie by day either dozes (in shade, or once the
     *  horde is sun-immune) or — while it still burns under open sky — heads for the nearest shade. The shade is
     *  pursued as a REMEMBERED SPOT so the full navigation (including breaking/pillaring through obstacles) carries
     *  it there, exactly like digging toward a heard noise; it dozes the instant it steps under cover. Engages only
     *  when the zombie isn't fleeing/sheltering/celebrating, and only for the "sleeper" majority (below the awake
     *  fraction that grows with the phase). Runs once per activation even while FROZEN, so a dozing zombie keeps
     *  checking whether it should wake. */
    private void handleDaySleep(ServerLevel level, long now, LivingEntity threat) {
        if (!ZombieMoodConfig.daySleepEnabled) {
            if (state == State.SLEEPING) {
                wake(now, false);
            }
            return;
        }
        // Sleep timers/NoAi are valid ONLY while actually SLEEPING. If another subsystem moved us out of it
        // (e.g. a hit forced FLEEING via the hysteresis block above, bypassing wake()), scrub the stale fields so
        // the next doze starts clean — otherwise a leftover armed wakeAt would instantly re-wake the next sleep.
        if (state != State.SLEEPING) {
            clearSleepState();
        }
        if (state != State.NORMAL && state != State.SLEEPING) {
            return; // busy fleeing / sheltering / celebrating — no dozing
        }
        boolean day = level.isBrightOutside();
        int phase = PhaseManager.current();
        // Sun-fire is deliberately NOT a disturbance: below the immunity phase an exposed zombie is ALWAYS on
        // fire, and reaching shade is exactly how it escapes that. Only a mob or NON-fire damage disturbs.
        boolean disturbed = threat != null || (entity.hurtTime > 0 && !entity.isOnFire());

        if (state == State.SLEEPING) {
            if (!day || disturbed || DaySleep.staysAwake(entity, phase)) {
                wake(now, false); // night fell / hit / promoted to the awake minority → back to the hunt
                return;
            }
            if (wakeAt != Long.MIN_VALUE && now >= wakeAt) {
                wake(now, true); // reaction delay elapsed → wake and head to the noise it heard
                return;
            }
            dozeInPlace(); // a dozing zombie is always dormant (FROZEN + NoAi-held)
            return;
        }

        // ---- state == NORMAL: hunt if roused, else shelter/sleep by day ----
        // A HIT keeps it awake to fight back.
        if (disturbed) {
            alertUntil = now + ZombieMoodConfig.daySleepAlertTicks;
        }
        // Stay AWAKE (hunt by sight AND sound via the normal brain, never doze) when: it's night; it's roused
        // (heard a noise / been hit within daySleepAlertTicks); it's investigating a heard spot; or it's in the
        // awake minority. A merely-SEEN silent player does NOT keep it up — that's the stealth, and gating on a
        // stable alert TIMER (not a per-tick audibility test) is what stops the chase<->doze stutter.
        boolean alert = now < alertUntil;
        boolean investigatingNoise = owner.hasTarget() && owner.targetEntity() == null && !sleepSeekingShade;
        if (!day || disturbed || alert || investigatingNoise || DaySleep.staysAwake(entity, phase)) {
            sleepSeekingShade = false; // busy hunting/investigating — abandon any shade-seek
            return;
        }

        // Idle daytime sleeper → SHELTER first (if it would burn under open sky), then doze.
        boolean exposed = DaySleep.burnsInSun(phase) && level.canSeeSky(entity.blockPosition());
        if (!exposed) {
            // In shade, or the horde is sun-immune → doze here. Snuff any residual sun-fire from the shade-run so
            // it isn't "asleep in the shade yet still on fire".
            if (sleepSeekingShade && entity.getRemainingFireTicks() > 0
                    && !level.canSeeSky(entity.blockPosition())) {
                entity.setRemainingFireTicks(0);
            }
            dozeInPlace();
            if (entity.onGround()) {
                state = State.SLEEPING; // only commit to SLEEPING once grounded+frozen; if it's still finishing a
                                        // leap/fall arc, dozeInPlace deferred — stay NORMAL and retry next tick.
            }
            return;
        }

        // Exposed and still burning → reach shade first (TOP priority, before dozing).
        if (owner.hasTarget()) {
            return; // already pathing to the shade memory — the brain breaks/pillars its way there
        }
        if (TargetingConfig.targetMemoryTicks <= 0) {
            return; // memory routing disabled → can't drive a shade-seek; keep roaming (it will burn)
        }
        BlockPos here = entity.blockPosition();
        // Skip the sweep while the last failure is still fresh AND we have not meaningfully moved. Moving
        // more than 4 blocks exposes genuinely new volume, so that always re-arms the search immediately.
        boolean moved = shadeFailedAt == null || shadeFailedAt.distSqr(here) > 16.0;
        if (!moved && now < shadeRetryAt) {
            return;
        }
        BlockPos shade = ShelterFinder.findShade(level, here, ZombieMoodConfig.shelterSearchRadius);
        if (shade == null) {
            sleepSeekingShade = false;
            shadeFailedAt = here;
            shadeRetryAt = now + ZombieMoodConfig.shelterRetryTicks;
            return; // no shade in range → keep roaming (can't help the burn)
        }
        shadeFailedAt = null;
        shadeRetryAt = Long.MIN_VALUE;
        // Remember the shade as a target so classify + the brain path AND break toward it, then doze on arrival.
        owner.pursuit().rememberTarget(shade.getX() + 0.5, shade.getY(), shade.getZ() + 0.5,
                now + TargetingConfig.targetMemoryTicks);
        sleepSeekingShade = true;
    }

    /** Hold the dozing pose: drop any hunt the classify pass seeded, stop moving, sync the sleep animation, and
     *  fall to FROZEN so a field of sleepers stays cheap (update() still runs each activation to check waking). */
    private void dozeInPlace() {
        // Kill any in-flight leap/pillar so its impulse can't carry the zombie while we settle it to sleep.
        owner.cancelClimb();
        // NEVER freeze a mid-air zombie. setNoAi(true) makes isEffectiveAi() false, and vanilla gates travel()
        // (the ONLY path that applies gravity + consumes deltaMovement) behind isEffectiveAi() — so a NoAi
        // airborne zombie stops falling and hangs frozen forever. While airborne, release any freeze we hold and
        // only stop pathing, so gravity lands it; we re-doze on a later activation once it's back on the ground.
        if (!entity.onGround()) {
            if (noAiFrozen) {
                entity.setNoAi(false);
                noAiFrozen = false;
            }
            entity.getNavigation().stop();
            return;
        }
        sleepSeekingShade = false;
        suppressHunt();
        entity.getNavigation().stop();
        // Grounded → freeze vanilla AI so RandomStrollGoal (default-on) can't walk the dozing zombie around.
        // Cleared on wake via clearSleepState(). Safe now because it's on the ground (gravity already applied).
        if (!noAiFrozen) {
            entity.setNoAi(true);
            noAiFrozen = true;
        }
        owner.setState(ZombieState.SLEEPING);
        owner.setLod(LODLevel.FROZEN);
    }

    private void suppressHunt() {
        entity.setTarget(null);
        owner.pursuit().clearTarget();
        owner.pursuit().clearMemory();
        owner.pursuit().clearSound();
    }

    /** Leave the SLEEPING state. When {@code investigate}, seed short-term memory with the last heard noise so
     *  the normal hunt (LODManager → brain) walks the zombie over to check it out. */
    private void wake(long now, boolean investigate) {
        alertUntil = now + ZombieMoodConfig.daySleepAlertTicks; // just roused → stay awake & hunt for a while
        if (investigate && hasWakePos && TargetingConfig.targetMemoryTicks > 0) {
            owner.pursuit().rememberTarget(wakeX, wakeY, wakeZ, now + TargetingConfig.targetMemoryTicks);
        }
        state = State.NORMAL;
        clearSleepState();
        owner.setLod(LODLevel.HIGH); // re-activate immediately so it starts moving this activation
    }

    /** Reset the wake TIMERS + release any NoAi we held. Called on wake and whenever the mood step notices we're
     *  no longer SLEEPING, so a stale armed wakeAt can't instantly re-wake the next doze. Deliberately does NOT
     *  touch sleepSeekingShade: that flag tracks an in-progress walk-to-shade across NORMAL activations, and is
     *  cleared where it belongs (on doze, on a real wake-up reason, or when no shade is found). */
    private void clearSleepState() {
        wakeAt = Long.MIN_VALUE;
        hasWakePos = false;
        if (noAiFrozen) {
            entity.setNoAi(false); // hand vanilla AI back exactly when WE were the ones holding it off
            noAiFrozen = false;
        }
    }

    /** Hand vanilla AI back if WE are holding it, without touching any other sleep state. Called when this
     *  mood object is about to be discarded — chunk unload or server stop — because {@code NoAI} is persisted
     *  to entity NBT by vanilla while {@code noAiFrozen} is not: a frozen zombie whose mood dies is reloaded
     *  with NoAI still true and nothing left that knows to lift it, leaving a gravity-less, navigation-less
     *  statue that setPersistenceRequired also stops from despawning (audit #2). Idempotent. */
    public void releaseAiHold() {
        if (noAiFrozen) {
            entity.setNoAi(false);
            noAiFrozen = false;
        }
    }

    /** Called by the sound bus for every zombie within earshot of a noise. It (re-)arms the ROUSED alert timer so
     *  the zombie stays awake and hunts by sight+sound — for an already-awake zombie that's all it does. For a
     *  SLEEPING one it also stashes the source and, after {@code daySleepWakeDelayTicks}, wakes it to investigate
     *  that spot. Continuous noise keeps re-arming the alert, so a chased zombie never lapses back into a doze. */
    public void notifyHeardSound(long now, double x, double y, double z) {
        alertUntil = now + ZombieMoodConfig.daySleepAlertTicks;
        if (state != State.SLEEPING) {
            return; // already awake — the alert refresh is enough; the hunt/investigate runs normally
        }
        wakeX = x;
        wakeY = y;
        wakeZ = z;
        hasWakePos = true;
        if (wakeAt == Long.MIN_VALUE) {
            wakeAt = now + ZombieMoodConfig.daySleepWakeDelayTicks;
        }
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

    /** What a fleeing zombie actually runs from: its recent aggressor if any, else — only while wounded (below
     *  {@code regainHealthFraction}) — the nearest nearby player, so a wounded zombie keeps fleeing when you walk
     *  up to it instead of freezing once the aggressor memory lapses. A healthy zombie gets null and hunts. */
    private LivingEntity flightThreat(LivingEntity aggressor, float frac) {
        if (aggressor != null) {
            return aggressor;
        }
        if (frac < ZombieMoodConfig.regainHealthFraction) {
            return MoodStateDispatch.nearestTargetablePlayer(entity, ZombieMoodConfig.fleeThreatRadius);
        }
        return null;
    }
}
