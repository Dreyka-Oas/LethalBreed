package com.dreykaoas.lethalbreed.effect.contamination;

import com.dreykaoas.lethalbreed.effect.contamination.symptom.ContaminationSymptoms;
import com.dreykaoas.lethalbreed.effect.contamination.symptom.SymptomEffects;

import com.dreykaoas.lethalbreed.config.domain.ContaminationConfig;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;

/**
 * The main per-server-tick sweep over tracked victims: cure roll, aging, latent/symptomatic branching, the
 * health/food pulse, episodes, hallucination, and level-up. Extracted out of the {@code ContaminationManager}
 * facade to keep it a thin delegator.
 */
public final class ContaminationTick {
    private ContaminationTick() {}

    // Reused snapshot buffer so the per-tick sweep can mutate `tracked` (cure/removals) mid-iteration without a
    // ConcurrentModificationException, WITHOUT allocating and rehashing a fresh HashSet every server tick.
    // Server-thread only, non-reentrant (nothing in the loop calls tick() again), so a static scratch is safe.
    private static final ArrayList<LivingEntity> SNAPSHOT = new ArrayList<>();

    /** Was the plague enabled on the previous tick? Drives the one-shot purge below. */
    private static boolean wasEnabled = true;

    public static void tick(MinecraftServer server) {
        // Cleared BEFORE the guard, not after: the two ordinary ways out of here, `tracked` going empty
        // (last victim cured or died) and the plague being switched off, both take the early return, and
        // a scratch buffer that only self-clears on the hot path holds its last batch forever. One retained
        // LivingEntity pins level -> ServerLevel -> chunks -> MinecraftServer (audit #8).
        SNAPSHOT.clear();

        boolean enabled = refreshEnabledState();
        if (!enabled || ContaminationState.TRACKED.isEmpty()) {
            return;
        }
        long t = server.getTickCount();
        SNAPSHOT.addAll(ContaminationState.TRACKED);
        for (int i = 0; i < SNAPSHOT.size(); i++) {
            LivingEntity e = SNAPSHOT.get(i);
            if (e == null || e.isRemoved() || !e.isAlive() || !(e.level() instanceof ServerLevel level)) {
                // Fully drop the victim from all six collections, not just `tracked`: an unloaded/dead/
                // dimension-changed entity left in the timer maps pins the whole world graph (audit #2).
                // Persistent attachments stay, so a chunk that reloads re-tracks the victim via onLoad.
                ContaminationLifecycle.forgetAllTransient(e);
                continue;
            }
            int c = ContaminationState.age(e);
            if (c <= 0) {
                ContaminationLifecycle.cure(e);
                continue;
            }

            // Cure: only by staying crouched; tiny random chance per check.
            if (e.isCrouching() && t % Math.max(1, ContaminationConfig.contamCureCheckTicks) == 0
                    && ContaminationRoll.percent(ContaminationState.RNG,
                            ContaminationConfig.contamCureMinPct, ContaminationConfig.contamCureMaxPct)) {
                ContaminationLifecycle.cure(e);
                continue;
            }

            c++;
            e.setAttached(ContaminationState.CONTAM, c);

            if (!ContaminationState.symptomatic(e)) {
                ContaminationSymptoms.tickLatent(e, t);
                continue;
            }

            SymptomEffects.apply(e, level, t);
        }
    }

    /** Reads the current enabled flag and fires the one-shot enabled→disabled purge when the plague was
     *  just switched off. */
    private static boolean refreshEnabledState() {
        boolean enabled = ContaminationConfig.contaminationEnabled;
        if (wasEnabled && !enabled) {
            // Enabled -> disabled: purge once, here. The sweep that would otherwise clean the in-memory
            // state is switched off by this very flag. Persistent attachments are untouched, so
            // re-enabling the plague re-tracks every victim through onLoad on its next chunk load (audit #9).
            ContaminationLifecycle.onServerStopped();
        }
        wasEnabled = enabled;
        return enabled;
    }

    static void clearSnapshot() {
        SNAPSHOT.clear();
        wasEnabled = true;
    }

}
