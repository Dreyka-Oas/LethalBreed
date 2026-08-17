package com.dreykaoas.lethalbreed.effect.contamination;

import com.dreykaoas.lethalbreed.effect.contamination.symptom.ContaminationEpisodes;
import com.dreykaoas.lethalbreed.effect.contamination.symptom.ContaminationEvolve;
import com.dreykaoas.lethalbreed.effect.contamination.symptom.ContaminationHallucination;
import com.dreykaoas.lethalbreed.effect.contamination.symptom.ContaminationSymptoms;

import com.dreykaoas.lethalbreed.config.domain.ContaminationConfig;
import com.dreykaoas.lethalbreed.effect.LethalBreedEffects;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;

/**
 * The main per-server-tick sweep over tracked victims: cure roll, aging, latent/symptomatic branching, the
 * health/food pulse, episodes, hallucination, and level-up. Extracted out of the {@code ContaminationManager}
 * facade to keep it a thin delegator.
 */
public final class ContaminationTick {
    private ContaminationTick() {}

    // Reused snapshot buffer so the per-tick sweep can mutate `tracked` (cure/removals) mid-iteration without a
    // ConcurrentModificationException — WITHOUT allocating and rehashing a fresh HashSet every server tick.
    // Server-thread only, non-reentrant (nothing in the loop calls tick() again), so a static scratch is safe.
    private static final ArrayList<LivingEntity> SNAPSHOT = new ArrayList<>();

    /** Was the plague enabled on the previous tick? Drives the one-shot purge below. */
    private static boolean wasEnabled = true;

    public static void tick(MinecraftServer server) {
        // Cleared BEFORE the guard, not after: the two ordinary ways out of here — `tracked` going empty
        // (last victim cured or died) and the plague being switched off — both take the early return, and
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
                // Fully drop the victim from all six collections, not just `tracked` — an unloaded/dead/
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

            applySymptomaticEffects(e, level, t);
        }
    }

    /** Reads the current enabled flag and fires the one-shot enabled→disabled purge when the plague was
     *  just switched off. */
    private static boolean refreshEnabledState() {
        boolean enabled = ContaminationConfig.contaminationEnabled;
        if (wasEnabled && !enabled) {
            // Enabled -> disabled: purge once, here, rather than leaving the in-memory state to be cleaned
            // by a sweep that this very flag switches off. Persistent attachments are untouched, so
            // re-enabling the plague re-tracks every victim through onLoad on its next chunk load (audit #9).
            ContaminationLifecycle.onServerStopped();
        }
        wasEnabled = enabled;
        return enabled;
    }

    /** Runs every active symptomatic effect for one victim this tick: icon upkeep, the creative/spectator
     *  pause, level-up roll, the health/food pulse, episodes, and hallucination. This is always the tail of
     *  the per-victim sweep, so the original loop's {@code continue} statements become {@code return} here. */
    private static void applySymptomaticEffects(LivingEntity e, ServerLevel level, long t) {
        // The skull icon is the symptomatic stage's only marker, so losing it means the plague is gone:
        // /effect clear wipes the attachments outright (EffectClearCuresPlagueMixin), and milk puts the
        // icon straight back (MilkKeepsPlagueMixin), so reaching here with no icon means something else
        // removed it — treat that as a cure rather than leaving a symptomatic victim with no marker.
        int lvl = ContaminationState.level(e);
        int wantAmp = Math.max(0, lvl - 1);
        MobEffectInstance cur = e.getEffect(LethalBreedEffects.SUPER_CONTAMINATION);
        if (cur == null) {
            ContaminationLifecycle.cure(e);
            return;
        }
        if (cur.getAmplifier() != wantAmp) {
            ContaminationSymptoms.applyIcon(e, wantAmp);
        }

        // A player who can't take normal damage (Creative / Spectator) keeps the plague — icon, level, everything
        // stays — but none of its active effects fire: no health/food pulse, no episodes, no hallucination, no
        // evolution. The moment they return to Survival/Adventure the symptoms resume from where they were.
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) {
            return;
        }

        // Level-up roll: every 1–2 in-game days a chance to climb toward maxLevel (recomputes intensity).
        ContaminationEvolve.tickEvolve(e, t);

        double mult = ContaminationState.intensity(e); // per-victim intensity for the current level

        // Slow plague pulse: every 5–10 real seconds (random per pulse) shave a small chip off BOTH health and
        // (players) food. Higher level → bigger chip (×mult). Zombies are never tracked, so it can't chip its
        // own kind. Only the final, fatal chip goes through the vanilla damage pipeline (death/reanimation).
        Long due = ContaminationState.NEXT_PULSE_TICK.get(e);
        if (due == null) {
            ContaminationState.NEXT_PULSE_TICK.put(e, t + rollIntervalTicks());
        } else if (t >= due) {
            float dmg = (float) (ContaminationRoll.uniform(ContaminationState.RNG,
                    ContaminationConfig.contamDamageMin, ContaminationConfig.contamDamageMax) * mult);
            float next = e.getHealth() - dmg;
            if (next > 0.0f) {
                e.setHealth(next);
            } else {
                e.hurtServer(level, e.damageSources().magic(), Float.MAX_VALUE);
            }
            if (e instanceof Player p) {
                // Exhaustion drains food gradually: 4.0 exhaustion = 1 food point, so this removes ~dmg food.
                p.getFoodData().addExhaustion(dmg * (float) ContaminationConfig.contamFoodExhaustionMult);
            }
            ContaminationState.NEXT_PULSE_TICK.put(e, t + rollIntervalTicks());
        }

        // Random episodic afflictions (slow / no-jump / weak-strike) — each on its own timer, scaled by mult.
        ContaminationEpisodes.tickEpisodes(e, t, mult);

        // Zombie-vision hallucination — a fourth episode on its own random timer. Applies/removes the transient
        // ZOMBIE_VISION effect; the victim's client reads it to draw other players as zombies.
        ContaminationHallucination.tickHallucination(e, t, mult);
    }

    /** Reset BOTH pieces of this class's static state — despite the name, not just the snapshot: it drops the
     *  scratch buffer's references to the closing world's entities AND re-arms the {@link #wasEnabled}
     *  enabled/disabled transition detector so the next server starts from a known state.
     *
     *  <p>Called from {@link ContaminationLifecycle#onServerStopped()}, which is itself reached both on
     *  SERVER_STOPPED and on the mid-tick enabled→disabled purge. On the mid-tick path the re-arm is
     *  immediately overwritten by {@code wasEnabled = enabled} at the end of the transition check, so that
     *  purge still fires exactly once; the re-arm is there for the SERVER_STOPPED path, where nothing else
     *  runs afterwards and the next server must not inherit a stale {@code false}. */
    static void clearSnapshot() {
        SNAPSHOT.clear();
        wasEnabled = true;
    }

    /** Roll the next pulse delay in ticks, uniform in [contamIntervalMinSec, contamIntervalMaxSec] × 20. */
    private static long rollIntervalTicks() {
        return ContaminationState.rollWindow(ContaminationConfig.contamIntervalMinSec,
                ContaminationConfig.contamIntervalMaxSec, 1.0);
    }
}
