package com.dreykaoas.lethalbreed.entity.mood;

import com.dreykaoas.lethalbreed.config.domain.ZombieMoodConfig;
import com.dreykaoas.lethalbreed.dimension.WorldAIContext;
import com.dreykaoas.lethalbreed.entity.LODLevel;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import com.dreykaoas.lethalbreed.util.Players;
import com.dreykaoas.lethalbreed.util.TargetSelector;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import static com.dreykaoas.lethalbreed.util.Scalars.sq;

import java.util.List;

/**
 * Per-state side effects driven once per {@code ZombieMood.update} activation: dropping the hunt while
 * fleeing/sheltering, keeping the LOD alive so the drive methods keep running, and firing the one-shot distress
 * scream + rally once the fleer has opened enough distance from its threat.
 */
public final class MoodStateDispatch {
    private MoodStateDispatch() {}

    /** ZombieMood's mood states. Owned here (rather than duplicated as a private enum on ZombieMood) so dispatch
     *  is a plain typed switch — no string bridge between the two classes. */
    public enum State { NORMAL, FLEEING, SHELTERING, CELEBRATING, SLEEPING }

    /** Returns true if the distress scream fired this call (caller bumps its own counter + latch). No-op for
     *  {@code NORMAL}. */
    public static boolean apply(State active, Zombie entity, ServerLevel level, SmartZombie owner,
            WorldAIContext ctx, LivingEntity threat, boolean alreadyScreamed) {
        if (active == State.SHELTERING) {
            dropHunt(entity, owner);
            owner.setLod(LODLevel.HIGH);
        } else if (active == State.FLEEING) {
            dropHunt(entity, owner);
            owner.setLod(LODLevel.HIGH); // keep AI active so driveFlee runs (would be FROZEN with no target)
            if (!alreadyScreamed && threat != null
                    && entity.distanceToSqr(threat) >= sq(ZombieMoodConfig.distressDistance)) {
                ZombieMoodSounds.scream(entity, level, ZombieMoodConfig.screamVolume, ZombieMoodConfig.distressPitch);
                ctx.soundBus().emit(entity.getX(), entity.getY(), entity.getZ(),
                        ZombieMoodConfig.distressRallyRadius);
                return true;
            }
        } else if (active == State.CELEBRATING) {
            owner.setLod(LODLevel.HIGH); // keep the pose + countdown alive even with no target
        }
        return false;
    }

    /** Drop the hunt: no melee target, no stale memory/sound pursuit. Also called directly by
     *  {@code ZombieMood.dozeInPlace()} (see {@link com.dreykaoas.lethalbreed.entity.ZombieMood}) when entering
     *  the SLEEPING state, which is not one of {@link MoodStateDispatch}'s own dispatched states. */
    public static void dropHunt(Zombie entity, SmartZombie owner) {
        entity.setTarget(null);
        owner.pursuit().clearTarget();
        owner.pursuit().clearMemory();
        owner.pursuit().clearSound();
    }

    /** True when a direct kill left the area clear of other prey (celebrate trigger). isValid rejects zombie
     *  kin, bosses, and dead entities (incl. the just-killed victim), so any survivor found here is genuine
     *  remaining prey. */
    public static boolean preyCleared(Zombie entity, ServerLevel level, double celebrateRadius) {
        AABB box = entity.getBoundingBox().inflate(celebrateRadius);
        List<LivingEntity> prey = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> TargetSelector.isValid(entity, e));
        return prey.isEmpty();
    }

    /** Whoever last damaged this zombie, if still alive and within {@code radius} (pure auto-detect, no
     *  hardcoded list). Vanilla clears the last-hurt-by memory after ~100 ticks, so a threat naturally
     *  "expires" once it stops fighting. */
    public static LivingEntity currentThreat(Zombie entity, double radius) {
        LivingEntity a = entity.getLastHurtByMob();
        if (a != null && a.isAlive() && !a.isRemoved() && entity.distanceToSqr(a) <= sq(radius)) {
            return a;
        }
        return null;
    }

    /** The nearest TARGETABLE player within {@code radius} — the fallback threat a WOUNDED zombie flees from
     *  even when nothing has hit it recently, so it keeps retreating while you stand near it instead of freezing
     *  once the "last hurt by" memory lapses. Creative/spectator players are ignored (same rule as targeting). */
    public static Player nearestTargetablePlayer(Zombie entity, double radius) {
        Player best = null;
        double bestSq = sq(radius);
        for (Player p : entity.level().players()) {
            if (!Players.isTargetable(p)) {
                continue;
            }
            double dSq = entity.distanceToSqr(p);
            if (dSq <= bestSq) {
                bestSq = dSq;
                best = p;
            }
        }
        return best;
    }
}
