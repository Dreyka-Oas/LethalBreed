package oas.dreyka.lethalbreed.effect.contamination;

import oas.dreyka.lethalbreed.config.domain.ContaminationConfig;
import oas.dreyka.lethalbreed.effect.LethalBreedEffects;
import oas.dreyka.lethalbreed.entity.spawn.SpawnFilter;
import oas.dreyka.lethalbreed.probe.DevProbe;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * What the plague does when its host dies: wipe the state, then raise the body if it was upright enough to
 * come back as a zombie.
 *
 * <p>Kept apart from {@link ContaminationLifecycle} because reanimation creates an entity where the rest of
 * the plague only edits one, and because the humanoid test is a rule people come looking for by itself
 * ("why did the creeper not get back up?").
 */
public final class ContaminationDeath {
    private ContaminationDeath() {}

    /** Death of a contaminated victim: clear the plague state, then reanimate as a zombie if it was a humanoid. */
    public static void onDeath(LivingEntity e, ServerLevel level) {
        if (ContaminationState.age(e) <= 0) {
            return;
        }
        ContaminationLifecycle.forgetAllTransient(e);
        e.removeEffect(LethalBreedEffects.ZOMBIE_VISION);
        e.removeAttached(ContaminationState.CONTAM);
        e.removeAttached(ContaminationState.SYMPTOMATIC);
        e.removeAttached(ContaminationState.LEVEL);
        e.removeAttached(ContaminationState.INTENSITY);
        PlagueDeadlines.strip(e);
        if (DevProbe.on()) {
            DevProbe.sink.count(DevProbe.DEATH, DevProbe.GLOBAL);
        }
        if (ContaminationConfig.contamReanimateHumanoids && isHumanoid(e)) {
            reanimate(e, level);
        }
    }

    /** Spawn a fresh zombie at the victim's death spot (its "reanimation"). Villagers rise as zombie villagers. */
    private static void reanimate(LivingEntity e, ServerLevel level) {
        var type = (e instanceof net.minecraft.world.entity.npc.villager.Villager)
                ? net.minecraft.world.entity.EntityType.ZOMBIE_VILLAGER
                : net.minecraft.world.entity.EntityType.ZOMBIE;
        var z = type.create(level, net.minecraft.world.entity.EntitySpawnReason.CONVERSION);
        if (z != null) {
            z.setPos(e.getX(), e.getY(), e.getZ());
            z.setYRot(e.getYRot());
            // The spawn filter would otherwise eat this body inside addFreshEntity: at phase 0 whatever it
            // is, and above it whenever onlyPlainZombie is on and the victim was a villager. See
            // SpawnFilter.spare: the plague's own conversions are not what that filter is for.
            SpawnFilter.spare(z);
            level.addFreshEntity(z);
        }
    }

    /** A biped the plague can raise into a zombie. Players always qualify; every other mob is auto-detected from
     *  its standing hitbox: tall, narrow and clearly upright. This is dynamic (no hardcoded mob list), so it
     *  covers villagers, piglins, illagers, witches, skeletons, endermen AND modded humanoids alike, while
     *  excluding creepers (too short), golems/quadrupeds (too wide) and small mobs. */
    public static boolean isHumanoid(LivingEntity e) {
        if (e instanceof Player) {
            return true;
        }
        float w = e.getBbWidth();
        float h = e.getBbHeight();
        // >= 1.75 tall drops the creeper (1.7); <= 0.7 wide drops iron/snow-golem-width & quadrupeds;
        // h >= 2.4×w keeps only genuinely upright, biped-shaped hitboxes. All three thresholds are configurable.
        return h >= (float) ContaminationConfig.contamReanimateMinHeight
                && w <= (float) ContaminationConfig.contamReanimateMaxWidth
                && h >= w * (float) ContaminationConfig.contamReanimateAspect;
    }

}
