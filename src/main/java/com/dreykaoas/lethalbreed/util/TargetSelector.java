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

    /** Nearest valid living target within {@code radius}, or null. A candidate is detected if the zombie can
     *  SEE it (opaque blocks block sight; glass/ice/leaves do not) OR HEAR it — anything within hearing range
     *  ({@code soundBaseRadius}) is sensed even through solid walls (zombies hear through walls by design). So
     *  a close entity hidden behind blocks still beats a far visible one: the nearest DETECTED entity wins. */
    public static LivingEntity findNearest(ServerLevel level, Mob self, double radius) {
        // The candidate AABB must cover the FULL detection reach, which is the max of the visual radius and the
        // loud-hearing reach (soundBaseRadius × soundLoudMultiplier). Inflating by radius alone dropped loud
        // entities sitting between radius and hearReach, silently truncating hearing.
        // Cap the hearing reach used to size the query box: soundBaseRadius (≤128) × soundLoudMultiplier (≤16)
        // could otherwise reach ~2048, and a 2048-block getEntitiesOfClass AABB would scan a colossal volume
        // every acquisition = a TPS cliff. 128 is well past any sane hearing range and matches the detect cap.
        double hearReach = TargetingConfig.soundEnabled
                ? Math.min(128.0, TargetingConfig.soundBaseRadius * Math.max(1.0, TargetingConfig.soundLoudMultiplier))
                : 0.0;
        AABB box = self.getBoundingBox().inflate(Math.max(radius, hearReach));
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, box, e -> isValid(self, e));
        // Hearing range scales with how LOUD the entity is, mirroring SoundEventBus: a normal noise carries
        // soundBaseRadius, a LOUD action (mining/attacking/placing = arm swing, like a block break) carries
        // soundBaseRadius × soundLoudMultiplier. -1 disables hearing entirely (soundEnabled off).
        double base = TargetingConfig.soundBaseRadius;
        double loud = base * TargetingConfig.soundLoudMultiplier;
        double baseHearSq = TargetingConfig.soundEnabled ? base * base : -1.0;
        double loudHearSq = TargetingConfig.soundEnabled ? loud * loud : -1.0;
        // Sort nearest-first so the FIRST detected candidate is the nearest detected one — then return
        // immediately. The detection test on the far miss-list is what's expensive (canSee is a voxel
        // raycast), so visiting candidates closest-first lets us stop after the fewest possible raycasts:
        // only the entities nearer than the winner (all unseen+unheard) ever get raycast.
        candidates.sort((a, b) -> Double.compare(self.distanceToSqr(a), self.distanceToSqr(b)));
        for (LivingEntity e : candidates) {
            double d = self.distanceToSqr(e);
            // Heard (close, through walls) OR seen (line of sight). Hearing requires the entity to be making
            // NOISE this tick (not mere proximity) — a motionless, silent entity emits no sound and can only
            // be acquired by sight. A loud action (arm swing) is heard from the louder radius; a mere footstep
            // only from the base radius. Only when LOS is required AND it's not heard do we fall back to
            // needing sight — a distant/quiet entity behind an opaque wall is unseen and unheard, so it isn't
            // a target until it makes itself known.
            boolean heard = isAudible(e) && d <= (e.swinging ? loudHearSq : baseHearSq);
            if (TargetingConfig.requireLineOfSight && !heard && !canSee(level, self, e)) {
                continue;
            }
            return e; // nearest detected (list is distance-sorted) — done
        }
        return null;
    }

    /** An entity is audible only when it actually emits noise this tick: walking (moved at least
     *  {@code soundMoveThreshold} horizontally and not sneaking), performing an action (arm swing =
     *  attack/place/break/mine, or using an item = eat/drink), or hurt (cry on taking damage / being hit /
     *  burning). A
     *  motionless, silent entity makes no sound and can only be acquired by line of sight. Mirrors the
     *  player-footstep rule in {@code SoundEventBus.tickPlayers} so hearing is consistent for all entities. */
    private static boolean isAudible(LivingEntity e) {
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
