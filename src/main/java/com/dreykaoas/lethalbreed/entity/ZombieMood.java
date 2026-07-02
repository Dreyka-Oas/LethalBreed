package com.dreykaoas.lethalbreed.entity;

import com.dreykaoas.lethalbreed.config.domain.ZombieMoodConfig;
import com.dreykaoas.lethalbreed.dimension.WorldAIContext;
import com.dreykaoas.lethalbreed.util.TargetSelector;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
    private enum State { NORMAL, FLEEING, SHELTERING, CELEBRATING }

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
                // A retreat only works if the fleer can actually outpace the threat. When the threat is at least
                // as fast (running from a same-/faster-speed pursuer just delays death), give up after far fewer
                // failed activations than the normal wall-blocked threshold and turn to fight instead.
                int giveUp = threatAtLeastAsFast(threat)
                        ? Math.min(ZombieMoodConfig.fleeFastThreatGiveUp, ZombieMoodConfig.fleeStuckActivations)
                        : ZombieMoodConfig.fleeStuckActivations;
                if (fleeStuckActivations >= giveUp) {
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

        // Sun-shelter override: a wounded zombie (already fleeing/sheltering) that is burning under open sky
        // breaks off the straight retreat and dashes to the nearest shade — burning to death while running in a
        // line is worse than a short detour into cover. Symmetric exit: once it's no longer on fire (or reached
        // a shaded block) it drops back to the plain FLEEING recover state.
        if (ZombieMoodConfig.sunShelterEnabled
                && (state == State.FLEEING || state == State.SHELTERING)
                && frac < ZombieMoodConfig.fleeHealthFraction) {
            boolean exposed = entity.isOnFire() && level.canSeeSky(entity.blockPosition());
            if (exposed) {
                if (shelterTarget == null || level.canSeeSky(shelterTarget)) {
                    shelterTarget = findShade(level); // (re)acquire a shaded refuge; may stay null if none near
                }
                state = State.SHELTERING;
            } else {
                // Safe now (in shade or fire out) — resume the ordinary wounded retreat/heal.
                shelterTarget = null;
                if (state == State.SHELTERING) {
                    state = State.FLEEING;
                }
            }
        } else if (state == State.SHELTERING) {
            shelterTarget = null; // no longer eligible (healed up / threat gone path handled above)
            state = State.FLEEING;
        }

        if (state == State.SHELTERING) {
            entity.setTarget(null);
            owner.pursuit().clearTarget();
            owner.pursuit().clearMemory();
            owner.pursuit().clearSound();
            owner.setLod(LODLevel.HIGH);
        } else if (state == State.FLEEING) {
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
        boolean regenEligible = (state == State.FLEEING || state == State.SHELTERING
                || state == State.CELEBRATING)
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

    /** Drive the dash to shade: path to the shaded refuge found in {@link #update}. Called each tick from the
     *  brain while sheltering. Falls back to a plain retreat when no shade was located (so a burning zombie in
     *  the open still moves rather than standing still and cooking). */
    public void driveShelter(ServerLevel level) {
        if (shelterTarget != null) {
            entity.getNavigation().moveTo(shelterTarget.getX() + 0.5, shelterTarget.getY(),
                    shelterTarget.getZ() + 0.5, ZombieMoodConfig.shelterSpeed);
            entity.getLookControl().setLookAt(shelterTarget.getX() + 0.5,
                    shelterTarget.getY() + 0.5, shelterTarget.getZ() + 0.5);
            return;
        }
        driveFlee(level); // no shade nearby — keep retreating from the threat rather than stalling in the sun
    }

    /** Search a horizontal ring around the zombie for the nearest standable block whose column is NOT under
     *  open sky (a shaded refuge). Returns the closest such foot position, or null when none is within range. */
    private BlockPos findShade(ServerLevel level) {
        BlockPos origin = entity.blockPosition();
        int r = ZombieMoodConfig.shelterSearchRadius;
        BlockPos best = null;
        double bestSq = Double.MAX_VALUE;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                int x = origin.getX() + dx;
                int z = origin.getZ() + dz;
                // Column is shaded if the sky is blocked at the zombie's own height there.
                m.set(x, origin.getY(), z);
                if (level.canSeeSky(m)) {
                    continue;
                }
                // Require a solid floor + head clearance so the zombie can actually stand there.
                if (!level.getBlockState(m.below()).isSolid()
                        || !level.getBlockState(m).isAir()
                        || !level.getBlockState(m.above()).isAir()) {
                    continue;
                }
                double distSq = origin.distSqr(m);
                if (distSq < bestSq) {
                    bestSq = distSq;
                    best = m.immutable();
                }
            }
        }
        return best;
    }

    /** True when the threat's movement speed is at least the fleer's effective flee speed — meaning a straight
     *  retreat can't open ground, so fleeing is futile and the fleer should give up sooner and fight. */
    private boolean threatAtLeastAsFast(LivingEntity threat) {
        double zombieBase = entity.getAttributeValue(Attributes.MOVEMENT_SPEED);
        double fleerSpeed = zombieBase * ZombieMoodConfig.fleeSpeed;
        double threatSpeed = threat.getAttributes().hasAttribute(Attributes.MOVEMENT_SPEED)
                ? threat.getAttributeValue(Attributes.MOVEMENT_SPEED)
                : zombieBase; // no speed attribute (e.g. a player uses a different model) → assume peer speed
        return threatSpeed >= fleerSpeed;
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
