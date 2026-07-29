package com.dreykaoas.lethalbreed.effect.contamination;

import com.dreykaoas.lethalbreed.config.domain.ContaminationConfig;
import com.dreykaoas.lethalbreed.effect.LethalBreedEffects;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Infection entry/exit points: contaminate, chunk-load re-tracking, death (+ humanoid reanimation), the outright
 * cure, and the dev tools that force-advance a victim's stage/level. Everything here mutates
 * {@link ContaminationState}'s shared maps/set.
 */
public final class ContaminationLifecycle {
    private ContaminationLifecycle() {}

    /** Infect a victim (called from the zombie-hit hook). No-op if already contaminated or it's a zombie.
     *  Starts LATENT: nothing visible, no plague damage — only a brief particleless slow right now. */
    public static void contaminate(LivingEntity e) {
        if (!ContaminationConfig.contaminationEnabled || e instanceof net.minecraft.world.entity.monster.zombie.Zombie
                || ContaminationState.age(e) > 0) {
            return;
        }
        e.setAttached(ContaminationState.CONTAM, 1);
        e.setAttached(ContaminationState.SYMPTOMATIC, false);
        ContaminationState.tracked.add(e);
        ContaminationSymptoms.applyLatentSlow(e);
        ContaminationState.INFECT_COUNT.incrementAndGet();
    }

    /** Re-track a contaminated entity after chunk reload (its attachment persists, the in-memory set doesn't).
     *  Only re-show the icon if it had already turned symptomatic.
     *
     *  <p>Gated on {@code contaminationEnabled}: the CONTAM attachment is persistent, so it outlives the
     *  option being switched off, while the tick sweep that would remove entries is itself gated on the same
     *  flag. Ungated, every chunk reload of a victim added a fresh HashSet entry — Entity.hashCode() is the
     *  monotonic entity id, so a reloaded victim is never equal to its previous incarnation (audit #9). */
    public static void onLoad(Entity e) {
        if (!ContaminationConfig.contaminationEnabled) {
            return;
        }
        if (e instanceof LivingEntity le && ContaminationState.age(le) > 0) {
            ContaminationState.tracked.add(le);
            if (ContaminationState.symptomatic(le)) {
                ContaminationSymptoms.applyIcon(le, ContaminationState.level(le) - 1);
            }
        }
    }

    /** Drop EVERY in-memory per-victim entry across all six contamination collections at once, without
     *  touching the persistent attachments (the victim re-tracks via {@link #onLoad} if it reloads).
     *
     *  <p>The single place the full six-collection purge lives. Audit #2: the per-tick invalid-entity branch
     *  used to drop only {@code tracked}, leaking the other five collections (four timer maps here + the
     *  episodes and hallucination maps in the sibling classes), each pinning a dead {@link LivingEntity} →
     *  its {@code ServerLevel} → the whole server graph. Routing cure, death AND the tick sweep through one
     *  method means a newly-added collection can't be forgotten by just one caller. */
    public static void forgetAllTransient(LivingEntity e) {
        ContaminationState.forgetTimers(e);
        ContaminationEpisodes.clearEpisodes(e);
        ContaminationHallucination.clear(e);
    }

    /** SERVER_STOPPED: drop ALL victims from every in-memory collection so a stopped world's entity graph is
     *  not pinned by these {@code static} maps into the next session. Persistent attachments are untouched.
     *
     *  <p>Every static collection in this package must be purged here. The tick sweep's scratch buffer was
     *  the one this list originally missed (audit #8) — it lives in a sibling class, so a purge written by
     *  reading only THIS file could not see it. When you add a static that holds an entity, add it here. */
    public static void onServerStopped() {
        ContaminationState.clearAllTransient();
        ContaminationEpisodes.clearAllVictims();
        ContaminationHallucination.clearAllVictims();
        ContaminationTick.clearSnapshot();
    }

    /** Death of a contaminated victim: clear the plague state, then reanimate as a zombie if it was a humanoid. */
    public static void onDeath(LivingEntity e, ServerLevel level) {
        if (ContaminationState.age(e) <= 0) {
            return;
        }
        forgetAllTransient(e);
        e.removeEffect(LethalBreedEffects.ZOMBIE_VISION);
        e.removeAttached(ContaminationState.CONTAM);
        e.removeAttached(ContaminationState.SYMPTOMATIC);
        e.removeAttached(ContaminationState.LEVEL);
        e.removeAttached(ContaminationState.INTENSITY);
        ContaminationState.DEATH_COUNT.incrementAndGet();
        if (ContaminationConfig.contamReanimateHumanoids && isHumanoid(e)) {
            reanimate(e, level);
        }
    }

    /** Dev tool: immediately surface symptoms on a contaminated victim (skips the 5–10 in-game-day roll), so the
     *  visible/damaging stage can be inspected on demand. No-op if the victim isn't contaminated. */
    public static void forceSymptomatic(LivingEntity e) {
        if (ContaminationState.age(e) <= 0 || ContaminationState.symptomatic(e)) {
            return;
        }
        e.setAttached(ContaminationState.SYMPTOMATIC, true);
        ContaminationState.setLevel(e, 1);
        ContaminationState.nextSymptomRoll.remove(e);
    }

    /** Dev tool: jump a victim straight to a plague level (infect + surface symptoms first if needed). Clamped
     *  to [1, maxLevel]. Rerolls the per-victim intensity for that level. */
    public static void forceLevel(LivingEntity e, int lvl) {
        if (ContaminationState.age(e) <= 0) {
            contaminate(e);
        }
        if (ContaminationState.age(e) <= 0) {
            return; // contamination disabled or entity ineligible
        }
        if (!ContaminationState.symptomatic(e)) {
            e.setAttached(ContaminationState.SYMPTOMATIC, true);
            ContaminationState.nextSymptomRoll.remove(e);
        }
        ContaminationState.setLevel(e, lvl);
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
            level.addFreshEntity(z);
        }
    }

    /** A biped the plague can raise into a zombie. Players always qualify; every other mob is auto-detected from
     *  its standing hitbox — tall, narrow and clearly upright. This is dynamic (no hardcoded mob list), so it
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

    public static void cure(LivingEntity e) {
        e.removeAttached(ContaminationState.CONTAM);
        e.removeAttached(ContaminationState.SYMPTOMATIC);
        e.removeEffect(LethalBreedEffects.SUPER_CONTAMINATION);
        e.removeEffect(LethalBreedEffects.ZOMBIE_VISION);
        ContaminationSymptoms.removeLatentSlow(e);
        e.removeAttached(ContaminationState.LEVEL);
        e.removeAttached(ContaminationState.INTENSITY);
        forgetAllTransient(e);
    }
}
