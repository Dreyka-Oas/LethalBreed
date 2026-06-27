package com.dreykaoas.lethalbreed.ai;

import com.dreykaoas.lethalbreed.config.LethalBreedConfig;
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
        LivingEntity target = TargetSelector.findNearest(level, sz.entity(), LethalBreedConfig.targetDetectRadius);

        LODLevel lod;
        if (target != null) {
            // Live detection (seen or heard) → the nearest DETECTED entity always wins, overriding memory.
            sz.setTarget(target, target.getX(), target.getY(), target.getZ());
            // Refresh short-term memory to the target's current spot, so if it slips out of sight+sound next
            // tick the zombie heads to where it last was instead of forgetting instantly.
            if (LethalBreedConfig.targetMemoryTicks > 0) {
                sz.rememberTarget(target.getX(), target.getY(), target.getZ(),
                        level.getGameTime() + LethalBreedConfig.targetMemoryTicks);
            }
            // With vanilla target goals stripped (forceNearestTarget), set the vanilla target here too so
            // melee + look track the nearest pick immediately, even when the zombie is mid-water/pillar.
            if (LethalBreedConfig.attackAllTargets && sz.entity().getTarget() != target) {
                sz.entity().setTarget(target);
            }
            lod = lodFromDistSq(sz.entity().distanceToSqr(target));
        } else if (LethalBreedConfig.targetMemoryTicks > 0 && sz.hasMemory()
                && level.getGameTime() < sz.memoryExpire()) {
            // Lost sight AND sound, but remember where it was — keep going there briefly (no live entity, so
            // no melee/vanilla target). Reached the spot with nothing there, or memory ran out → forget.
            sz.setMemoryTarget();
            sz.entity().setTarget(null);
            double d = sz.distanceToTargetSq();
            double arrive = LethalBreedConfig.soundArriveDistance;
            if (d <= arrive * arrive) {
                sz.clearTarget();
                sz.clearMemory();
                lod = LODLevel.FROZEN;
            } else {
                lod = lodFromDistSq(d);
            }
        } else {
            sz.clearTarget();
            sz.clearMemory();
            lod = LODLevel.FROZEN;
        }
        sz.setLod(lod);
        return lod;
    }

    private static LODLevel lodFromDistSq(double d) {
        double high = LethalBreedConfig.lodHigh * LethalBreedConfig.lodHigh;
        double med = LethalBreedConfig.lodMedium * LethalBreedConfig.lodMedium;
        double low = LethalBreedConfig.lodLow * LethalBreedConfig.lodLow;
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
