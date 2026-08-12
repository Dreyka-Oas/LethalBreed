package com.dreykaoas.lethalbreed.effect.contamination.symptom;

import com.dreykaoas.lethalbreed.effect.contamination.ContaminationLifecycle;
import com.dreykaoas.lethalbreed.effect.contamination.ContaminationRoll;
import com.dreykaoas.lethalbreed.effect.contamination.ContaminationState;

import com.dreykaoas.lethalbreed.config.domain.ContaminationConfig;
import com.dreykaoas.lethalbreed.effect.LethalBreedEffects;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * The latent stage (one-shot slow + symptom-surfacing roll) and the effect icon. Split out of
 * {@link ContaminationLifecycle} to keep both under the file-size limit.
 */
public final class ContaminationSymptoms {
    private ContaminationSymptoms() {}

    private static final Identifier LATENT_SLOW_ID =
            Identifier.fromNamespaceAndPath("lethalbreed", "contam_latent_slow");

    /** Latent stage: no icon, no damage. Expire the one-shot slow, and every 5–10 in-game days roll a small
     *  chance to surface symptoms (which flips the victim into the visible/damaging stage). */
    public static void tickLatent(LivingEntity e, long t) {
        // Retire the brief infection slow once its window is up.
        Long slowEnd = ContaminationState.latentSlowUntil.get(e);
        if (slowEnd != null && t >= slowEnd) {
            removeLatentSlow(e);
            ContaminationState.latentSlowUntil.remove(e);
        }

        Long roll = ContaminationState.nextSymptomRoll.get(e);
        if (roll == null) {
            ContaminationState.nextSymptomRoll.put(e, t + rollSymptomIntervalTicks());
            return;
        }
        if (t >= roll) {
            if (ContaminationRoll.percent(ContaminationState.RNG,
                    ContaminationConfig.contamSymptomMinPct, ContaminationConfig.contamSymptomMaxPct)) {
                e.setAttached(ContaminationState.SYMPTOMATIC, true);
                ContaminationState.setLevel(e, 1); // enter symptomatic at level 1 (applies icon + seeds intensity)
                ContaminationState.nextSymptomRoll.remove(e);
            } else {
                ContaminationState.nextSymptomRoll.put(e, t + rollSymptomIntervalTicks());
            }
        }
    }

    /** Roll the next symptom-trigger delay in ticks, uniform in [minDays, maxDays] × 24000. */
    private static long rollSymptomIntervalTicks() {
        return ContaminationState.rollScaled(ContaminationConfig.contamSymptomMinDays,
                ContaminationConfig.contamSymptomMaxDays, 1.0, 24000.0);
    }

    /** Apply the brief, particleless latent slow as a transient movement-speed modifier. Its removal tick is
     *  computed from the current server tick so {@link #tickLatent} can strip it after the short window. */
    public static void applyLatentSlow(LivingEntity e) {
        AttributeInstance inst = e.getAttribute(Attributes.MOVEMENT_SPEED);
        if (inst == null || ContaminationConfig.contamLatentSlowAmount <= 0.0
                || !(e.level() instanceof ServerLevel level)) {
            return;
        }
        inst.addOrUpdateTransientModifier(new AttributeModifier(
                LATENT_SLOW_ID, -ContaminationConfig.contamLatentSlowAmount,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        long now = level.getServer().getTickCount();
        ContaminationState.latentSlowUntil.put(e, now + Math.max(1, ContaminationConfig.contamLatentSlowTicks));
    }

    public static void removeLatentSlow(LivingEntity e) {
        AttributeInstance inst = e.getAttribute(Attributes.MOVEMENT_SPEED);
        if (inst != null) {
            inst.removeModifier(LATENT_SLOW_ID);
        }
    }

    public static void applyIcon(LivingEntity e, int amplifier) {
        // ambient=false, visible=false (NO swirling particles — our plague is silent), showIcon=true (skull only).
        // Amplifier mirrors (level-1) so the client scales its screen overlay from the effect it already syncs.
        e.addEffect(new MobEffectInstance(LethalBreedEffects.SUPER_CONTAMINATION,
                MobEffectInstance.INFINITE_DURATION, Math.max(0, amplifier), false, false, true));
    }
}
