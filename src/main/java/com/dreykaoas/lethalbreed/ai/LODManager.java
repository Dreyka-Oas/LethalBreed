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
        LivingEntity target = TargetSelector.findNearest(level, sz.entity(), TargetingConfig.targetDetectRadius);

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
            if (TargetingConfig.attackAllTargets && sz.entity().getTarget() != target) {
                sz.entity().setTarget(target);
            }
            lod = lodFromDistSq(sz.entity().distanceToSqr(target));
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
                lod = lodFromDistSq(d);
            }
        } else {
            sz.pursuit().clearTarget();
            sz.pursuit().clearMemory();
            lod = LODLevel.FROZEN;
        }
        sz.setLod(lod);
        return lod;
    }

    private static LODLevel lodFromDistSq(double d) {
        // Enforce monotonic tier radii (high <= medium <= low). The three are independent config knobs, so a
        // misordered value (e.g. lodMedium <= lodHigh) would otherwise let an earlier branch swallow a whole
        // tier silently. Clamping each tier up to the previous keeps classification predictable.
        double highR = SchedulerConfig.lodHigh;
        double medR = Math.max(SchedulerConfig.lodMedium, highR);
        double lowR = Math.max(SchedulerConfig.lodLow, medR);
        double high = highR * highR;
        double med = medR * medR;
        double low = lowR * lowR;
        if (d <= high) {
            return LODLevel.HIGH;
        } else if (d <= med) {
            return LODLevel.MEDIUM;
        } else if (d <= low) {
            return LODLevel.LOW;
        }
        return LODLevel.FROZEN;
    }
}
