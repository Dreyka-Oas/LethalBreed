package com.dreykaoas.lethalbreed.ai;

import com.dreykaoas.lethalbreed.config.domain.SchedulerConfig;
import com.dreykaoas.lethalbreed.config.domain.TargetingConfig;

import com.dreykaoas.lethalbreed.entity.LODLevel;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import com.dreykaoas.lethalbreed.util.TargetSelector;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/**
 * Acquires each zombie's nearest valid target (any living entity except bosses/other zombies) and
 * classifies its LOD from the distance to that target. No target in range → FROZEN.
 */
public final class LODManager {
    private LODManager() {}

    public static LODLevel classify(SmartZombie sz, ServerLevel level) {
        // Apply forceNearestTarget live (strip/restore vanilla target goals) before acquiring our own pick.
        sz.reconcileTargetingGoals();
        LivingEntity target = TargetSelector.findNearest(level, sz.entity(), TargetingConfig.targetDetectRadius);

        LODLevel prev = sz.lod();
        LODLevel lod;
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
        } else if (TargetingConfig.targetMemoryTicks > 0 && sz.pursuit().hasMemory()
                && level.getGameTime() < sz.pursuit().memoryExpire()) {
            // Lost sight AND sound, but remember where it was — keep going there briefly (no live entity, so
            // no melee/vanilla target). Reached the spot with nothing there, or memory ran out → forget.
            sz.pursuit().setMemoryTarget();
            sz.entity().setTarget(null);
            double d = sz.pursuit().distanceToTargetSq();
            double arrive = TargetingConfig.soundArriveDistance;
            if (d <= arrive * arrive) {
                sz.pursuit().clearTarget();
                sz.pursuit().clearMemory();
                lod = LODLevel.FROZEN;
            } else {
                lod = lodFromDistSq(d, prev);
            }
        } else {
            sz.pursuit().clearTarget();
            sz.pursuit().clearMemory();
            lod = LODLevel.FROZEN;
        }
        sz.setLod(lod);
        return lod;
    }

    private static LODLevel lodFromDistSq(double d, LODLevel prev) {
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
        double high = sq(highR + (prev == LODLevel.HIGH ? h : 0.0));
        double med = sq(medR + (prev == LODLevel.HIGH || prev == LODLevel.MEDIUM ? h : 0.0));
        double low = sq(lowR + (prev != LODLevel.FROZEN ? h : 0.0));
        if (d <= high) {
            return LODLevel.HIGH;
        } else if (d <= med) {
            return LODLevel.MEDIUM;
        } else if (d <= low) {
            return LODLevel.LOW;
        }
        return LODLevel.FROZEN;
    }

    private static double sq(double v) {
        return v * v;
    }
}
