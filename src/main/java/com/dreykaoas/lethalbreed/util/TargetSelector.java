package com.dreykaoas.lethalbreed.util;

import com.dreykaoas.lethalbreed.config.LethalBreedConfig;
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
        return !e.isSpectator();
    }

    /** Nearest valid living target within {@code radius}, or null. A candidate is detected if the zombie can
     *  SEE it (opaque blocks block sight; glass/ice/leaves do not) OR HEAR it — anything within hearing range
     *  ({@code soundBaseRadius}) is sensed even through solid walls (zombies hear through walls by design). So
     *  a close entity hidden behind blocks still beats a far visible one: the nearest DETECTED entity wins. */
    public static LivingEntity findNearest(ServerLevel level, Mob self, double radius) {
        AABB box = self.getBoundingBox().inflate(radius);
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, box, e -> isValid(self, e));
        double hearSq = LethalBreedConfig.soundEnabled
                ? LethalBreedConfig.soundBaseRadius * LethalBreedConfig.soundBaseRadius : -1.0;
        LivingEntity best = null;
        double bestSq = Double.MAX_VALUE;
        for (LivingEntity e : candidates) {
            double d = self.distanceToSqr(e);
            if (d >= bestSq) {
                continue;
            }
            // Heard (close, through walls) OR seen (line of sight). Hearing requires the entity to be making
            // NOISE this tick (not mere proximity) — a motionless, silent entity emits no sound and can only
            // be acquired by sight. Only when LOS is required AND it's not heard do we fall back to needing
            // sight — a distant/quiet entity behind an opaque wall is unseen and unheard, so it isn't a target
            // until it makes itself known.
            boolean heard = d <= hearSq && isAudible(e);
            if (LethalBreedConfig.requireLineOfSight && !heard && !canSee(level, self, e)) {
                continue;
            }
            bestSq = d;
            best = e;
        }
        return best;
    }

    /** An entity is audible only when it actually emits noise this tick: walking (moved at least
     *  {@code soundMoveThreshold} horizontally and not sneaking), performing an action (arm swing =
     *  attack/place/break/mine, or using an item = eat/drink), or just hurt (cry on taking damage). A
     *  motionless, silent entity makes no sound and can only be acquired by line of sight. Mirrors the
     *  player-footstep rule in {@code SoundEventBus.tickPlayers} so hearing is consistent for all entities. */
    private static boolean isAudible(LivingEntity e) {
        Vec3 v = e.getDeltaMovement();
        double hMove = Math.sqrt(v.x * v.x + v.z * v.z); // horizontal only — ignore gravity on a standing mob
        boolean walking = hMove >= LethalBreedConfig.soundMoveThreshold && !e.isCrouching();
        boolean acting = e.swinging || e.isUsingItem();  // place / break / mine / eat / drink
        boolean hurt = e.hurtTime > 0;
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
