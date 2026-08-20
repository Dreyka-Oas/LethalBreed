package com.dreykaoas.lethalbreed.ai;

import com.dreykaoas.lethalbreed.config.domain.engine.SchedulerConfig;
import com.dreykaoas.lethalbreed.config.domain.TargetingConfig;

import com.dreykaoas.lethalbreed.entity.LodLevel;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import com.dreykaoas.lethalbreed.special.SpecialBehavior;
import com.dreykaoas.lethalbreed.util.target.TargetSelector;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import static com.dreykaoas.lethalbreed.util.Scalars.sq;

/**
 * Acquires each zombie's nearest valid target (any living entity except bosses/other zombies) and
 * classifies its LOD from the distance to that target. No target in range → FROZEN.
 */
public final class LodManager {
    private LodManager() {}

    public static LodLevel classify(SmartZombie sz, ServerLevel level,
                                    com.dreykaoas.lethalbreed.spatial.TargetIndex index) {
        // Apply forceNearestTarget live (strip/restore vanilla target goals) before acquiring our own pick.
        sz.reconcileTargetingGoals();
        // Pass the currently-committed target so the pick is sticky: a zombie mid-dig keeps its prey instead of
        // flipping to a marginally-closer other the instant the wall it's breaking blocks line of sight.
        LivingEntity current = sz.pursuit().targetEntity();
        LivingEntity target = TargetSelector.findNearest(level, sz.entity(),
                TargetingConfig.targetDetectRadius, current, index);

        LodLevel prev = sz.lod();
        LodLevel lod;
        if (target != null) {
            // Live detection (seen or heard) → the nearest DETECTED entity always wins, overriding memory.
            sz.pursuit().setTarget(target, target.getX(), target.getY(), target.getZ());
            // Refresh short-term memory to the target's current spot, so if it slips out of sight+sound next
            // tick the zombie heads to where it last was instead of forgetting instantly.
            if (TargetingConfig.targetMemoryTicks > 0) {
                sz.pursuit().rememberTarget(target.getX(), target.getY(), target.getZ(),
                        level.getGameTime() + TargetingConfig.targetMemoryTicks);
            }
            // With vanilla target goals stripped (forceNearestTarget), set the vanilla target here too so
            // melee + look track the nearest pick immediately, even when the zombie is mid-water/pillar.
            // forceNearestTarget=true → we own the selection, so always retarget to our nearest pick.
            // forceNearestTarget=false → vanilla target goals still run, so only seed a target when there is
            // none (don't stomp vanilla's choice every tick, which made the toggle inoperative).
            if (TargetingConfig.attackAllTargets) {
                if (TargetingConfig.forceNearestTarget) {
                    if (sz.entity().getTarget() != target) {
                        sz.entity().setTarget(target);
                    }
                } else if (sz.entity().getTarget() == null) {
                    sz.entity().setTarget(target);
                }
            }
            lod = lodFromDistSq(sz.entity().distanceToSqr(target), prev);
        } else {
            lod = TargetFallback.classify(sz, level, prev);
        }
        // A Bomber whose fuse is lit has committed to detonating, so it must keep being ticked to get there.
        // LodBucketPass drops a FROZEN zombie before tick() runs, which would stop the fuse mid-burn and turn
        // it into a dormant mine — one that goes off the moment a player wanders back within range, since the
        // deadline it wakes up to is long past. LOW still runs the special, just on the distance throttle, so
        // detonation can land a few activations late; that is the whole cost of keeping it honest.
        if (lod == LodLevel.FROZEN && SpecialBehavior.fuseIsLit(sz.entity())) {
            lod = LodLevel.LOW;
        }
        sz.setLod(lod);
        return lod;
    }

    /** True if the zombie has a clear line of sight to the spot (no solid block between its eyes and it) — i.e.
     *  it has genuinely reached it, not just gotten close on the far side of a wall it still has to break. */
    static boolean canSeeSpot(ServerLevel level, LivingEntity e, double x, double y, double z) {
        Vec3 from = e.getEyePosition();
        Vec3 to = new Vec3(x, y + 0.5, z);
        HitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e));
        return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(to) <= 1.0;
    }

    static LodLevel lodFromDistSq(double d, LodLevel prev) {
        // Enforce monotonic tier radii (high <= medium <= low). The three are independent config knobs, so a
        // misordered value (e.g. lodMedium <= lodHigh) would otherwise let an earlier branch swallow a whole
        // tier silently. Clamping each tier up to the previous keeps classification predictable.
        double highR = SchedulerConfig.lodHigh;
        double medR = Math.max(SchedulerConfig.lodMedium, highR);
        double lowR = Math.max(SchedulerConfig.lodLow, medR);
        // One-sided hysteresis: a zombie keeps its current (closer) tier until it crosses that tier's outer
        // edge by more than lodHysteresis blocks. Upgrades (moving inward) snap at the plain boundary; only
        // downgrades get the slack — so a zombie idling on a boundary stops flip-flopping tier + re-pathing.
        double h = Math.max(0.0, SchedulerConfig.lodHysteresis);
        double high = sq(highR + (prev == LodLevel.HIGH ? h : 0.0));
        double med = sq(medR + (prev == LodLevel.HIGH || prev == LodLevel.MEDIUM ? h : 0.0));
        double low = sq(lowR + (prev != LodLevel.FROZEN ? h : 0.0));
        if (d <= high) {
            return LodLevel.HIGH;
        } else if (d <= med) {
            return LodLevel.MEDIUM;
        } else if (d <= low) {
            return LodLevel.LOW;
        }
        return LodLevel.FROZEN;
    }
}
