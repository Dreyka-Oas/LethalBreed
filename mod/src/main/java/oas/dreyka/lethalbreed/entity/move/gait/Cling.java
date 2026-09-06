package oas.dreyka.lethalbreed.entity.move.gait;

import oas.dreyka.lethalbreed.config.domain.move.LeapConfig;
import oas.dreyka.lethalbreed.entity.SmartZombie;
import oas.dreyka.lethalbreed.entity.ZombieState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;

/**
 * The cling: a leap that comes down on its prey latches there for a few seconds and gnaws instead of
 * bouncing off. Owns its cooldown, its rolled duration and the banked damage, the way {@link Leap} next
 * door owns the leap cooldown.
 *
 * <p><b>Not a vanilla passenger.</b> {@code Entity.startRiding} refuses the mount server-side whenever the
 * vehicle's type cannot be serialized, and {@code EntityType.PLAYER} is declared {@code noSave()}, so a
 * zombie can never ride a player, which is the whole point of the feature. The guard is sound: a passenger
 * is written inside its vehicle's data and a player is never written at all, so the zombie would be lost on
 * the next save. It is pinned onto the victim by hand instead, where a passenger would have sat when the
 * ceiling allows it, and a cling ends if the world is reloaded under it, leaving nothing behind on the
 * zombie: no flag of ours is persisted, which is what makes that clean.
 *
 * <p><b>Two cadences.</b> {@link #follow()} runs every tick from {@code EveryTickPass} or the zombie trails
 * a block behind a running player. {@link #tick} runs once per activation and is told how much game time
 * that activation stood for: a frozen zombie is woken one activation in {@code frozenReclassifyDivisor} and
 * would otherwise gnaw four times too slowly.
 */
public final class Cling {

    /** Ticks of vanilla invulnerability during which a hit no bigger than the last one is swallowed whole. */
    private static final int HURT_WINDOW = 10;

    private final SmartZombie owner;
    private final Zombie entity;
    private LivingEntity victim;
    private int ticksLeft;
    private int cooldown;
    private double owed;

    public Cling(SmartZombie owner) {
        this.owner = owner;
        this.entity = owner.entity();
    }

    public boolean active() { return victim != null; }

    /**
     * Latch onto {@code target} when the leap still resolving has brought the zombie onto it. Returns true
     * once latched, and the caller then leaves the zombie alone: it is being placed, not steered.
     */
    public boolean tryCling(LivingEntity target, boolean leaping) {
        if (!LeapConfig.leapClingEnabled || !leaping || active() || cooldown > 0
                || target == null || !target.isAlive()
                || !ClingMath.contact(entity.getBoundingBox(), target.getBoundingBox())) {
            return false;
        }
        victim = target;
        ticksLeft = ClingMath.durationTicks(entity.getRandom().nextDouble());
        owed = 0.0;
        entity.getNavigation().stop();
        owner.setState(ZombieState.CLINGING);
        // Placing it is left to follow(): LodBucketPass collects a zombie that latched during the bucket
        // run it just finished, and the every-tick pass places it before this same tick ends.
        return true;
    }

    /** Once per activation: burn the cooldown, spend {@code activationTicks} of the clock, gnaw. */
    public void tick(ServerLevel level, int activationTicks) {
        if (cooldown > 0) {
            cooldown--;
        }
        if (!active()) {
            return;
        }
        if (lost()) {
            release();
            return;
        }
        int step = Math.max(1, activationTicks);
        ticksLeft -= step;
        gnaw(level, step);
        if (ticksLeft <= 0) {
            release();
        }
    }

    /** Once per tick: hold the zombie on its prey, where {@link ClingMath#perch} says there is room. */
    public void follow() {
        if (!active()) {
            return;
        }
        Vec3 feet = lost() ? null : ClingMath.perch(entity, victim);
        if (feet == null) {
            release();
            return;
        }
        // Re-asserted here, not only at the latch: the brain guards run ahead of the hunt and any of them
        // may have moved the state, which would drop the synced flag the renderer poses from.
        owner.setState(ZombieState.CLINGING);
        // Gravity is left alone on purpose. Vanilla persists NoGravity from the moment it is true, no cling
        // survives a reload to clear it again, and a zombie that comes back floating never paths or leaps
        // again (audit #2, in its other flag). The pin below is the last word of a server tick, after every
        // entity has moved and after the trackers have gone out, so the fall it undoes is never seen.
        entity.setDeltaMovement(Vec3.ZERO);
        entity.resetFallDistance();
        entity.setPos(feet.x, feet.y, feet.z);
        entity.setYRot(victim.getYRot());
        entity.setYHeadRot(victim.getYRot());
    }

    private boolean lost() {
        return !owner.isValid() || victim.isRemoved() || !victim.isAlive()
                || victim.level() != entity.level();
    }

    private void gnaw(ServerLevel level, int step) {
        owed += ClingMath.damageFor(step);
        // Vanilla swallows a hit no bigger than the last one taken inside the ten-tick window, so a steady
        // quarter-heart chip would land every other activation and pay half of what it promises. Bank it
        // until the window is open, then hand it over whole.
        if (victim.invulnerableTime > HURT_WINDOW) {
            return;
        }
        Vec3 held = victim.getDeltaMovement();
        victim.hurtServer(level, level.damageSources().mobAttack(entity), (float) owed);
        // A mob hit shoves its victim, and vanilla answers a zero direction with a random one plus a jump's
        // worth of lift, so gnawing from overhead would bounce the prey twice a second. A cling holds what
        // it caught, so the shove goes straight back.
        victim.setDeltaMovement(held);
        owed = 0.0;
    }

    private void release() {
        victim = null;
        ticksLeft = 0;
        owed = 0.0;
        cooldown = LeapConfig.leapClingCooldownActivations;
        if (owner.state() == ZombieState.CLINGING) {
            owner.setState(ZombieState.PURSUING_PLAYER);
        }
    }
}
