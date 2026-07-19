package com.dreykaoas.lethalbreed.util;

import com.dreykaoas.lethalbreed.config.domain.TargetingConfig;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Picks what a zombie hunts: the nearest living entity, EXCEPT bosses (Ender Dragon, Wither) and other
 * zombies (so they don't gridlock targeting each other). Creative/spectator players are excluded so
 * you can observe.
 */
public final class TargetSelector {
    private TargetSelector() {}

    public static boolean isValid(Mob self, LivingEntity e) {
        if (e == self || !e.isAlive() || e.isRemoved()) {
            return false;
        }
        if (e instanceof Zombie) {
            return false; // own kind (zombie / husk / zombie villager / zombified piglin)
        }
        if (e instanceof EnderDragon || e instanceof WitherBoss) {
            return false; // bosses
        }
        if (e.getBbHeight() > 5.0f) {
            return false; // too tall (giants / large modded mobs) — never attack these
        }
        if (e instanceof ArmorStand) {
            return false; // not a creature
        }
        if (e instanceof Player p) {
            return Players.isTargetable(p); // creative/spectator excluded
        }
        if (TargetingConfig.targetPlayersOnly) {
            return false; // players-only mode: reject every non-player living entity
        }
        return !e.isSpectator();
    }

    /** Nearest valid living target that the zombie can SEE within {@code radius}, or null. VISION ONLY — sight
     *  primes over sound by design: a live combat target is acquired only by line of sight (opaque blocks block
     *  it; glass/ice/leaves do not). Hearing is handled separately by the sound bus: a heard noise feeds
     *  the zombie's short-term MEMORY so it walks to investigate the SPOT, and this seen target overrides that
     *  memory in {@code LODManager} the instant something comes into view. So a zombie hears a mob move behind a
     *  wall and commits to the noise location; once it rounds the wall and sees the mob, sight takes over. */
    /** Sticky variant: prefer the already-committed {@code current} target over a marginally-closer new one
     *  (see {@link TargetingConfig#targetSwitchMargin}). While a zombie digs through a wall toward its prey the
     *  wall blocks LOS to that prey, so the plain nearest-visible pick would flip to whatever else is in view
     *  and abandon the block — this keeps it committed until something is genuinely closer. */
    public static LivingEntity findNearest(ServerLevel level, Mob self, double radius, LivingEntity current) {
        LivingEntity best = findNearest(level, self, radius);
        double margin = TargetingConfig.targetSwitchMargin;
        if (margin <= 1.0 || current == null || current == self || !isValid(self, current)) {
            return best;
        }
        double curSq = self.distanceToSqr(current);
        if (curSq > radius * radius) {
            return best; // committed target left the detection radius → let the fresh pick take over
        }
        if (best == null) {
            return current; // nothing else detected → stay committed and keep digging toward it
        }
        // Keep current unless the new candidate is closer than current ÷ margin (compare in squared space).
        return curSq <= self.distanceToSqr(best) * margin * margin ? current : best;
    }

    public static LivingEntity findNearest(ServerLevel level, Mob self, double radius) {
        AABB box = self.getBoundingBox().inflate(radius);
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, box, e -> isValid(self, e));
        // Shuffle first so entities that end up EXACTLY tied (same distance band AND same height gap) resolve
        // at random — List.sort is stable, so it preserves this randomised order for equal keys.
        for (int i = candidates.size() - 1; i > 0; i--) {
            int j = self.getRandom().nextInt(i + 1);
            LivingEntity tmp = candidates.get(i);
            candidates.set(i, candidates.get(j));
            candidates.set(j, tmp);
        }
        // Sort nearest-first, but treat distances within TIE_BAND as "equally close": among two roughly-as-close
        // candidates (e.g. one overhead, one at our level) prefer the one nearest in HEIGHT — a target at the
        // zombie's own level is reachable without a climb, so it wins over one perched above at the same range.
        // Exact ties (same band + same height) keep the random order from the shuffle above.
        final double TIE_BAND = 4.0; // 4 = (2 blocks)²: distances differing by <2 blocks count as equal
        candidates.sort((a, b) -> {
            long ba = (long) (self.distanceToSqr(a) / TIE_BAND);
            long bb = (long) (self.distanceToSqr(b) / TIE_BAND);
            if (ba != bb) {
                return Long.compare(ba, bb);
            }
            double dya = Math.abs(a.getY() - self.getY());
            double dyb = Math.abs(b.getY() - self.getY());
            return Double.compare(dya, dyb);
        });
        double radiusSq = radius * radius;
        for (LivingEntity e : candidates) {
            // SIGHT only: within the visual detect radius AND (if required) an unobstructed line of sight.
            // List is distance-sorted, so the first visible candidate is the nearest visible one.
            boolean seen = self.distanceToSqr(e) <= radiusSq
                    && (!TargetingConfig.requireLineOfSight || canSee(level, self, e));
            if (seen) {
                return e; // nearest seen — done
            }
        }
        return null;
    }

    /** An entity is audible only when it actually emits noise this tick: walking (moved at least
     *  {@code soundMoveThreshold} horizontally and not sneaking), performing an action (arm swing =
     *  attack/place/break/mine, or using an item = eat/drink), or hurt (cry on taking damage / being hit /
     *  burning). A
     *  motionless, silent entity makes no sound and can only be acquired by line of sight. Mirrors the
     *  player-footstep rule in {@code SoundEventBus.tickPlayers} so hearing is consistent for all entities. */
    public static boolean isAudible(LivingEntity e) {
        Vec3 v = e.getDeltaMovement();
        double hMove = Math.sqrt(v.x * v.x + v.z * v.z); // horizontal only — ignore gravity on a standing mob
        boolean walking = hMove >= TargetingConfig.soundMoveThreshold && !e.isCrouching();
        boolean acting = e.swinging || e.isUsingItem();  // place / break / mine / eat / drink
        boolean hurt = e.hurtTime > 0 || e.isOnFire();   // taking damage / being hit / burning
        return walking || acting || hurt;
    }

    /** Line of sight from the zombie's eyes to the target's, treating only OPAQUE blocks as vision
     *  blockers — translucent blocks (glass, ice, leaves) are see-through. Coarse voxel walk (cheap). */
    private static boolean canSee(ServerLevel level, Mob self, LivingEntity target) {
        Vec3 from = self.getEyePosition();
        Vec3 to = target.getEyePosition();
        Vec3 delta = to.subtract(from);
        double dist = delta.length();
        if (dist < 1.0e-3) {
            return true;
        }
        double step = 0.5;
        int steps = (int) (dist / step);
        double sx = delta.x / dist * step;
        double sy = delta.y / dist * step;
        double sz = delta.z / dist * step;
        double cx = from.x, cy = from.y, cz = from.z;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int i = 1; i < steps; i++) {
            cx += sx; cy += sy; cz += sz;
            m.set(Mth.floor(cx), Mth.floor(cy), Mth.floor(cz));
            BlockState s = level.getBlockState(m);
            if (s.canOcclude()) {
                return false; // opaque full block → sight blocked (glass/leaves/ice do not occlude)
            }
        }
        return true;
    }
}
