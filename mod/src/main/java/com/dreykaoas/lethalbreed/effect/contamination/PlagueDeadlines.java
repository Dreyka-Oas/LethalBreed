package com.dreykaoas.lethalbreed.effect.contamination;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;

/**
 * The save-file half of three of {@link ContaminationState}'s four timer maps.
 *
 * <p>Infection level, intensity and the symptomatic flag were already persistent attachments, so a victim came
 * back from a reload at exactly the plague stage it went in at. Its four deadlines did not: they lived only in
 * static maps rebuilt empty at boot, so every reopened world handed the victim a fresh pulse, a fresh symptom
 * roll and a fresh level-up roll. A player who quit and rejoined at the right moment pushed the next symptom
 * out indefinitely, at no cost, forever.
 *
 * <p>The maps stay the working store; each one is mirrored here into a persistent attachment on the victim
 * itself, and {@link #reload} refills the map from that attachment when the entity loads. Writing through one
 * pair of methods is what keeps the two halves from drifting apart. The fourth map is the exception noted
 * below.
 *
 * <p>Deadlines are absolute world ages ({@code ServerLevel.getGameTime()}), which is why they can be stored at
 * all: the world age is itself saved and does not move while the world is closed, so a deadline written before
 * a shutdown still means the same instant afterwards. Against {@code MinecraftServer.getTickCount()}, which
 * restarts at 0 every launch, a stored deadline would come back looking hours into the future.
 */
public final class PlagueDeadlines {
    private PlagueDeadlines() {}

    public static final AttachmentType<Long> PULSE = persistent("contam_next_pulse");
    public static final AttachmentType<Long> SYMPTOM_ROLL = persistent("contam_next_symptom_roll");
    public static final AttachmentType<Long> EVOLVE_ROLL = persistent("contam_next_evolve_roll");

    // LATENT_SLOW_UNTIL_TICK is deliberately absent, and it is the fourth timer. It says when to take off an
    // attribute modifier that was added transiently, so the modifier is already gone by the time the victim
    // reloads; a restored deadline would sit in the map waiting to remove something nobody applied. The
    // retrack suite reads exactly that as a stranded entry, and it is right to.

    /** Loading this class is what registers the three types; see {@code ContaminationManager.init}. */
    public static void init() {}

    private static AttachmentType<Long> persistent(String path) {
        return AttachmentRegistry.createPersistent(
                Identifier.fromNamespaceAndPath("lethalbreed", path), Codec.LONG);
    }

    /** Record a deadline in the live map and on the victim, so a reload finds it again. */
    public static void set(Map<LivingEntity, Long> live, AttachmentType<Long> saved, LivingEntity e, long due) {
        live.put(e, due);
        e.setAttached(saved, due);
    }

    /** Retire a deadline that has been reached or made irrelevant, from both halves. */
    public static void clear(Map<LivingEntity, Long> live, AttachmentType<Long> saved, LivingEntity e) {
        live.remove(e);
        e.removeAttached(saved);
    }

    /** Refill the timer maps from what the victim carries. Called when a contaminated entity loads. */
    public static void reload(LivingEntity e) {
        adopt(ContaminationState.NEXT_PULSE_TICK, PULSE, e);
        adopt(ContaminationState.NEXT_SYMPTOM_ROLL_TICK, SYMPTOM_ROLL, e);
        adopt(ContaminationState.NEXT_EVOLVE_ROLL_TICK, EVOLVE_ROLL, e);
    }

    /** Strip the saved deadlines outright, for cure and death. Distinct from
     *  {@link ContaminationState#forgetTimers}, which only empties the maps: an unloading victim must keep its
     *  deadlines, a cured one must not, or its next infection would inherit them. */
    public static void strip(LivingEntity e) {
        e.removeAttached(PULSE);
        e.removeAttached(SYMPTOM_ROLL);
        e.removeAttached(EVOLVE_ROLL);
    }

    private static void adopt(Map<LivingEntity, Long> live, AttachmentType<Long> saved, LivingEntity e) {
        Long due = e.getAttached(saved);
        if (due != null) {
            live.put(e, due);
        }
    }
}
