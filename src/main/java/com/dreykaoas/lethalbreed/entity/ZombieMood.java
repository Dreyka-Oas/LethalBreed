package com.dreykaoas.lethalbreed.entity;

import com.dreykaoas.lethalbreed.config.domain.ZombieMoodConfig;
import com.dreykaoas.lethalbreed.dimension.WorldAIContext;
import com.dreykaoas.lethalbreed.util.TargetSelector;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Per-zombie "mood" on top of the hunt. Three linked behaviours, all config-gated by {@link ZombieMoodConfig}:
 *
 * <ul>
 *   <li><b>Celebrate</b> — after landing a direct kill with no other prey nearby, raise the arms and let out a
 *       loud triumphant groan for a short latch (see {@link #tryCelebrate}).</li>
 *   <li><b>Flee</b> — once health drops below {@link ZombieMoodConfig#fleeHealthFraction} while a threat is
 *       around, drop the target and retreat directly away from it; once far enough, scream for help — a sound
 *       event that rallies idle (unfocused) zombies to the fleer's position.</li>
 *   <li><b>Regen</b> — while fleeing OR celebrating and still below {@link ZombieMoodConfig#regainHealthFraction},
 *       slowly self-heal ({@link ZombieMoodConfig#regenAmount} every {@link ZombieMoodConfig#regenIntervalTicks}).</li>
 * </ul>
 *
 * <p>Flee/regen use a hysteresis band: entered below {@code fleeHealthFraction}, left at/above
 * {@code regainHealthFraction} — so a zombie between the two keeps whatever it was doing (no flip-flop).
 * {@link #update} runs once per activation from {@code LodBucketPass} (before the FROZEN skip, so a targetless
 * fleeing/celebrating zombie still ticks); {@link #driveFlee} runs from {@code ZombieBrain} while fleeing.
 */
public final class ZombieMood {
    private enum State { NORMAL, FLEEING, CELEBRATING }

    /** Dev instrumentation: incremented each time a fleer fires its distress scream + rally emit. */
    public static final java.util.concurrent.atomic.AtomicInteger DISTRESS_COUNT =
            new java.util.concurrent.atomic.AtomicInteger();

    private final Zombie entity;
    private final SmartZombie owner;

    private State state = State.NORMAL;
    private long celebrateUntil = Long.MIN_VALUE;
    private long lastRegenTime = 0L;
    private boolean distressScreamed = false;
    // Cornered-flee tracking: if the fleer can't open distance from its threat (wall-blocked / player right on
    // top of it), it gives up retreating and fights instead of standing passively.
    private double lastThreatDistSq = -1.0;
    private int fleeStuckActivations = 0;
    private long corneredUntil = Long.MIN_VALUE;

    public ZombieMood(Zombie entity, SmartZombie owner) {
        this.entity = entity;
        this.owner = owner;
    }

    public boolean isFleeing() {
        return state == State.FLEEING;
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

        // Celebration latch expires on its own. If the zombie is still hurt, roll straight into the wounded
        // FLEEING/recover state so it keeps healing up to the regain fraction.
        if (state == State.CELEBRATING && now >= celebrateUntil) {
            entity.setAggressive(false);
            state = frac < ZombieMoodConfig.regainHealthFraction ? State.FLEEING : State.NORMAL;
            distressScreamed = false;
        }

        // Flee hysteresis: ENTER below fleeHealthFraction while a threat is around; LEAVE only once healed back
        // to regainHealthFraction. Between the two it stays wounded (Schmitt trigger, no fight/flee flip-flop).
        // Note the exit does NOT depend on the threat: a fleer that outran its attacker keeps recovering (stands
        // and heals) until it reaches the regain fraction, instead of stopping after a single heal tick when
        // the vanilla last-attacker memory expires. Celebrating overrides entering flee (area is already clear).
        if (state == State.FLEEING) {
            if (frac >= ZombieMoodConfig.regainHealthFraction) {
                state = State.NORMAL;
                distressScreamed = false;
                resetFleeStuck();
            } else if (threat != null) {
                // Cornered check: track distance to the threat across activations. If the fleer keeps failing to
                // gain ground (wall behind it, or the threat pins it in place) it abandons the retreat and turns
                // to fight, latching a short cooldown so it doesn't immediately flip back into a blocked flee.
                double d = entity.distanceToSqr(threat);
                boolean gaining = lastThreatDistSq < 0.0 || d > lastThreatDistSq + 0.25;
                fleeStuckActivations = gaining ? 0 : fleeStuckActivations + 1;
                lastThreatDistSq = d;
                if (fleeStuckActivations >= ZombieMoodConfig.fleeStuckActivations) {
                    state = State.NORMAL;
                    distressScreamed = false;
                    corneredUntil = now + ZombieMoodConfig.corneredFightTicks;
                    resetFleeStuck();
                }
            } else {
                resetFleeStuck(); // threat gone (outran / disengaged): keep healing, drop stuck tracking
            }
        } else if (state != State.CELEBRATING && now >= corneredUntil
                && frac < ZombieMoodConfig.fleeHealthFraction && threat != null) {
            state = State.FLEEING;
            distressScreamed = false;
            lastRegenTime = now;
            resetFleeStuck();
        }

        if (state == State.FLEEING) {
            // Drop the hunt: no melee target, no stale memory/sound pursuit.
            entity.setTarget(null);
            owner.pursuit().clearTarget();
            owner.pursuit().clearMemory();
            owner.pursuit().clearSound();
            owner.setLod(LODLevel.HIGH); // keep AI active so driveFlee runs (would be FROZEN with no target)
            // Distress scream ONCE per episode, only after opening enough distance from a live threat.
            if (!distressScreamed && threat != null
                    && entity.distanceToSqr(threat) >= sq(ZombieMoodConfig.distressDistance)) {
                scream(level, ZombieMoodConfig.distressPitch);
                // Rally: idle (targetless) zombies within earshot path to the fleer via the sound bus.
                ctx.soundBus().emit(entity.getX(), entity.getY(), entity.getZ(),
                        ZombieMoodConfig.distressRallyRadius);
                distressScreamed = true;
                DISTRESS_COUNT.incrementAndGet();
            }
        } else if (state == State.CELEBRATING) {
            owner.setLod(LODLevel.HIGH); // keep the pose + countdown alive even with no target
        }

        // Self-heal while fleeing or celebrating and still hurt; otherwise hold the timer at "now" so the next
        // eligible spell waits a full interval before its first heal.
        boolean regenEligible = (state == State.FLEEING || state == State.CELEBRATING)
                && frac < ZombieMoodConfig.regainHealthFraction;
        if (regenEligible) {
            if (now - lastRegenTime >= ZombieMoodConfig.regenIntervalTicks) {
                entity.heal((float) ZombieMoodConfig.regenAmount);
                lastRegenTime = now;
            }
        } else {
            lastRegenTime = now;
        }
    }

    /** Drive the retreat: path directly away from the current threat. Called each tick from the brain while
     *  fleeing. Uses vanilla navigation, so the fleer freely climbs/descends terrain on the way out. */
    public void driveFlee(ServerLevel level) {
        LivingEntity threat = currentThreat();
        if (threat == null) {
            entity.getNavigation().stop(); // outran the threat → hold position and lick wounds while healing
            return;
        }
        Vec3 away = entity.position().subtract(threat.position());
        if (away.horizontalDistanceSqr() < 1.0e-4) {
            away = new Vec3(1.0, 0.0, 0.0); // degenerate (directly overlapping) → arbitrary direction
        }
        away = away.normalize().scale(ZombieMoodConfig.fleeDistance);
        entity.getNavigation().moveTo(entity.getX() + away.x, entity.getY(), entity.getZ() + away.z,
                ZombieMoodConfig.fleeSpeed);
        entity.getLookControl().setLookAt(entity.getX() + away.x, entity.getEyeY(), entity.getZ() + away.z);
    }

    /** A zombie landed a direct kill: if the area is now clear of other prey, celebrate (arms up + a loud
     *  triumphant groan). No-op when another valid target still lurks within {@code celebrateRadius}. */
    public void tryCelebrate(ServerLevel level) {
        if (!ZombieMoodConfig.moodEnabled || !entity.isAlive()) {
            return;
        }
        AABB box = entity.getBoundingBox().inflate(ZombieMoodConfig.celebrateRadius);
        // isValid rejects zombie kin, bosses, dead entities (incl. the just-killed victim) — so any survivor
        // here is genuine remaining prey and the kill did NOT clear the area.
        List<LivingEntity> prey = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> TargetSelector.isValid(entity, e));
        if (!prey.isEmpty()) {
            return;
        }
        state = State.CELEBRATING;
        celebrateUntil = level.getGameTime() + ZombieMoodConfig.celebrateTicks;
        lastRegenTime = level.getGameTime();
        entity.setAggressive(true); // raises the zombie's arms client-side
        owner.setState(ZombieState.CELEBRATING);
        scream(level, ZombieMoodConfig.victoryPitch);
    }

    private void resetFleeStuck() {
        fleeStuckActivations = 0;
        lastThreatDistSq = -1.0;
    }

    private LivingEntity currentThreat() {
        // Pure auto-detect: whoever last damaged this zombie (player, golem, any mob — no hardcoded list).
        // Vanilla clears this after ~100 ticks, so a threat naturally "expires" once it stops fighting.
        LivingEntity a = entity.getLastHurtByMob();
        if (a != null && a.isAlive() && !a.isRemoved()
                && entity.distanceToSqr(a) <= sq(ZombieMoodConfig.fleeThreatRadius)) {
            return a;
        }
        return null;
    }

    /** Play the amplified vanilla zombie groan at this zombie. Volume &gt;1 widens the audible range. */
    private void scream(ServerLevel level, float pitch) {
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                SoundEvents.ZOMBIE_AMBIENT, entity.getSoundSource(),
                ZombieMoodConfig.screamVolume, pitch);
    }

    private static double sq(double v) {
        return v * v;
    }
}
