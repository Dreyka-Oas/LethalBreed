package oas.dreyka.lethalbreed.util;

import oas.dreyka.lethalbreed.mixin.MobGoalsAccessor;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Hold a mob where it stands without taking its AI away, and give it back afterwards.
 *
 * <p>Stopping the navigation is not enough on its own, and that gap is worth spelling out because it costs
 * an afternoon to rediscover. {@code MoveControl} writes a walk input onto the mob with {@code setZza}, and
 * {@code travel} keeps consuming that input every tick until somebody writes a different one: an emptied
 * path leaves the last speed still pushing. Worse, a goal that wants to move is free to start again on the
 * very next tick and hand out a fresh path, and clearing a zombie's target is exactly what wakes the idle
 * wander goal up.
 *
 * <p>So the lock is vanilla's own: {@code Goal.Flag.MOVE} is disabled on the goal selector, which stops
 * every running goal that claims it and refuses to start another, then the leftover walk input is zeroed.
 * Falling is untouched, since gravity never went through either of them.
 *
 * <p>That disable does not stay put by itself. {@code Mob.tick} calls {@code updateControlFlags} every fifth
 * tick and writes all three flags back from scratch, so a lock set once survives at most five ticks and then
 * silently lapses. An armed Bomber picked its wander goal back up mid-fuse and strolled half a block before
 * the next activation parked it again, which read as a flaky arena rather than as a lock that does not hold.
 * {@link #held} is the register the {@code Mob.updateControlFlags} mixin consults to put the flag back.
 */
public final class MovementLock {
    private MovementLock() {}

    /**
     * Every mob currently parked.
     *
     * <p>Weak keys, because a held mob can perfectly well be removed while held: an armed Bomber ends its
     * fuse by detonating, and nothing gets to call {@link #release} on a corpse. A strong set would hold one
     * entry per detonation for the life of the server.
     */
    private static final Set<Mob> HELD = Collections.newSetFromMap(new WeakHashMap<>());

    /** Park the mob. Idempotent, so a per-activation caller can just call it. */
    public static void hold(Mob mob) {
        synchronized (HELD) {
            HELD.add(mob);
        }
        ((MobGoalsAccessor) mob).lethalbreed$goalSelector().disableControlFlag(Goal.Flag.MOVE);
        mob.getNavigation().stop();
        mob.setZza(0.0f);
        mob.setXxa(0.0f);
    }

    /** Hand movement back. Only meaningful after a {@link #hold}, and harmless otherwise. */
    public static void release(Mob mob) {
        synchronized (HELD) {
            HELD.remove(mob);
        }
        ((MobGoalsAccessor) mob).lethalbreed$goalSelector().enableControlFlag(Goal.Flag.MOVE);
    }

    /** True while this mob is parked, asked once every five ticks by the control-flag mixin. */
    public static boolean held(Mob mob) {
        synchronized (HELD) {
            return HELD.contains(mob);
        }
    }
}
