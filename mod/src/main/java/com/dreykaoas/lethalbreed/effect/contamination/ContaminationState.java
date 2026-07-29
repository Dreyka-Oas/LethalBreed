package com.dreykaoas.lethalbreed.effect.contamination;

import com.dreykaoas.lethalbreed.config.domain.ContaminationConfig;
import com.dreykaoas.lethalbreed.config.domain.ExpertConfig;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * The plague's persistent attachments, the {@code tracked} victim set, the transient per-victim timer maps, the
 * shared RNG, and the age/symptomatic/level/intensity accessors. The single source of truth other contamination
 * classes (and {@link com.dreykaoas.lethalbreed.effect.ContaminationManager}, which re-exports the attachments
 * for external callers) build on — this class has no dependency of its own outside this package.
 */
public final class ContaminationState {
    private ContaminationState() {}

    /** Contamination age in ticks; >0 means contaminated. Persistent → survives reload, milk can't clear it. */
    public static final AttachmentType<Integer> CONTAM = AttachmentRegistry.createPersistent(
            Identifier.fromNamespaceAndPath("lethalbreed", "contamination"), Codec.INT);
    /** Symptoms visible yet? Persistent. While false the plague is latent (hidden, no damage). */
    public static final AttachmentType<Boolean> SYMPTOMATIC = AttachmentRegistry.createPersistent(
            Identifier.fromNamespaceAndPath("lethalbreed", "contam_symptomatic"), Codec.BOOL);
    /** Plague level 1..maxLevel (symptomatic only). Persistent. Also mirrored into the skull effect's amplifier
     *  (level-1) so the client can scale its screen overlay without an extra packet. */
    public static final AttachmentType<Integer> LEVEL = AttachmentRegistry.createPersistent(
            Identifier.fromNamespaceAndPath("lethalbreed", "contam_level"), Codec.INT);
    /** Per-victim intensity multiplier for the CURRENT level (random jitter, so two victims differ). Persistent
     *  so it survives reload; recomputed on each level-up. */
    public static final AttachmentType<Double> INTENSITY = AttachmentRegistry.createPersistent(
            Identifier.fromNamespaceAndPath("lethalbreed", "contam_intensity"), Codec.DOUBLE);

    // Dev instrumentation (headless harness).
    public static final java.util.concurrent.atomic.AtomicInteger INFECT_COUNT = new java.util.concurrent.atomic.AtomicInteger();
    public static final java.util.concurrent.atomic.AtomicInteger DEATH_COUNT = new java.util.concurrent.atomic.AtomicInteger();

    public static final Set<LivingEntity> tracked = new HashSet<>();
    /** Server-tick of the next plague pulse per victim (transient; reseeded on load). */
    public static final java.util.Map<LivingEntity, Long> nextPulse = new java.util.HashMap<>();
    /** Server-tick of the next latent symptom-trigger roll per victim (transient; reseeded on load). */
    public static final java.util.Map<LivingEntity, Long> nextSymptomRoll = new java.util.HashMap<>();
    /** Server-tick at which the latent slow modifier should be removed per victim (transient). */
    public static final java.util.Map<LivingEntity, Long> latentSlowUntil = new java.util.HashMap<>();
    /** Server-tick of the next level-up roll per victim (transient; reseeded on load). */
    public static final java.util.Map<LivingEntity, Long> nextEvolveRoll = new java.util.HashMap<>();
    public static final Random RNG = new Random();

    public static int age(LivingEntity e) {
        Integer v = e.getAttached(CONTAM);
        return v == null ? 0 : v;
    }

    public static boolean symptomatic(LivingEntity e) {
        Boolean v = e.getAttached(SYMPTOMATIC);
        return v != null && v;
    }

    /** Current plague level (1..maxLevel). 0/absent → treat as level 1 for a symptomatic victim. */
    public static int level(LivingEntity e) {
        Integer v = e.getAttached(LEVEL);
        return v == null || v < 1 ? 1 : Math.min(v, Math.max(1, ContaminationConfig.contamMaxLevel));
    }

    /** Per-victim intensity multiplier for the current level (≥ 1.0). Recomputed on each level-up. */
    public static double intensity(LivingEntity e) {
        Double v = e.getAttached(INTENSITY);
        return v == null || v < 1.0 ? 1.0 : v;
    }

    /** Roll a fresh per-victim intensity for a level: 1 + (level-1) × step × jitter, jitter random per victim. */
    public static void recomputeIntensity(LivingEntity e, int lvl) {
        double jitter = ContaminationRoll.uniform(RNG,
                ContaminationConfig.contamLevelJitterMin, ContaminationConfig.contamLevelJitterMax);
        double mult = 1.0 + (lvl - 1) * ContaminationConfig.contamLevelStep * jitter;
        e.setAttached(INTENSITY, Math.max(1.0, mult));
    }

    /** Set a victim's level (clamped), reroll its intensity, and re-apply the icon so the amplifier updates. */
    public static void setLevel(LivingEntity e, int lvl) {
        int max = Math.max(1, ContaminationConfig.contamMaxLevel);
        lvl = Math.max(1, Math.min(lvl, max));
        e.setAttached(LEVEL, lvl);
        recomputeIntensity(e, lvl);
        ContaminationSymptoms.applyIcon(e, lvl - 1);
    }

    /** Forget every transient timer/tracking entry for a victim (on cure/death). Does not touch the persistent
     *  attachments themselves — callers strip those separately since cure and death clear a different subset.
     *  Covers only THIS class's collections; the two sibling maps (episodes, hallucination) are purged by
     *  {@link ContaminationLifecycle#forgetAllTransient} — use that, not this, to fully drop a victim. */
    public static void forgetTimers(LivingEntity e) {
        tracked.remove(e);
        nextPulse.remove(e);
        nextSymptomRoll.remove(e);
        latentSlowUntil.remove(e);
        nextEvolveRoll.remove(e);
    }

    /** Drop every victim from this class's in-memory collections at once (server stop). Persistent
     *  attachments are untouched — they live in entity NBT and re-track via {@link ContaminationLifecycle#onLoad}. */
    public static void clearAllTransient() {
        tracked.clear();
        nextPulse.clear();
        nextSymptomRoll.clear();
        latentSlowUntil.clear();
        nextEvolveRoll.clear();
    }

    /** Positive plague time-compression factor (dev command sets it; clamped to ≥ 1e-3 to avoid div-by-zero). */
    public static double devTimeScale() {
        return Math.max(ExpertConfig.expertContamTimeScaleFloor, ContaminationConfig.contamDevTimeScale);
    }

    /** Roll a random [minSec, maxSec] window as ticks × factor, honouring dev time-compression. */
    public static long rollWindow(double min, double max, double factor) {
        return rollScaled(min, max, factor, 20.0); // seconds → 20 ticks
    }

    /** The single [min,max]×factor uniform roll behind every plague timer: draw the range via
     *  {@link ContaminationRoll#uniform}, scale it, and convert to ticks via {@code unitTicks}
     *  (20 = per-second, 24000 = per-in-game-day) under dev compression. */
    public static long rollScaled(double min, double max, double factor, double unitTicks) {
        double v = ContaminationRoll.uniform(RNG, min, max) * factor;
        return Math.max(1L, Math.round(v * unitTicks / devTimeScale()));
    }
}
