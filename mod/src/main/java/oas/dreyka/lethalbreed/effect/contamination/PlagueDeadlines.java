package oas.dreyka.lethalbreed.effect.contamination;

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
 * back from a reload at exactly the plague stage it went in at. Its deadlines did not: they lived only in
 * static maps rebuilt empty at boot, so every reopened world handed the victim a fresh pulse, a fresh symptom
 * roll and a fresh level-up roll. A player who quit and rejoined at the right moment pushed the next symptom
 * out indefinitely, at no cost, forever.
 *
 * <p>The maps stay the working store; each one is mirrored onto the victim itself, and {@link #reload} refills
 * the map from that attachment when the entity loads. Writing through {@link #set} and {@link #clear} is what
 * keeps the two halves from drifting apart.
 *
 * <p>Deadlines are absolute world ages ({@code ServerLevel.getGameTime()}), which is why they can be stored at
 * all: the world age is itself saved and does not move while the world is closed, so a deadline written before
 * a shutdown still means the same instant afterwards. Against {@code MinecraftServer.getTickCount()}, which
 * restarts at 0 every launch, a stored deadline would come back looking hours into the future.
 */
public final class PlagueDeadlines {
    private PlagueDeadlines() {}

    /**
     * One saved deadline: the live map it is read from and the attachment it is written to, bound together in
     * a single value. Split across two parameters they were a two-part key a caller could mismatch, writing
     * one timer's instant under another timer's name with nothing to complain.
     *
     * <p>{@code LATENT_SLOW_UNTIL_TICK} is deliberately not here, and it is the fourth timer. It says when to
     * take off an attribute modifier that was added transiently, so the modifier is already gone by the time
     * the victim reloads; a restored deadline would sit in the map waiting to remove something nobody applied.
     * The retrack suite reads exactly that as a stranded entry, and it is right to.
     */
    public enum Deadline {
        PULSE(ContaminationState.NEXT_PULSE_TICK, "contam_next_pulse"),
        SYMPTOM_ROLL(ContaminationState.NEXT_SYMPTOM_ROLL_TICK, "contam_next_symptom_roll"),
        EVOLVE_ROLL(ContaminationState.NEXT_EVOLVE_ROLL_TICK, "contam_next_evolve_roll");

        private final Map<LivingEntity, Long> live;
        private final AttachmentType<Long> saved;

        Deadline(Map<LivingEntity, Long> live, String path) {
            this.live = live;
            this.saved = AttachmentRegistry.createPersistent(
                    Identifier.fromNamespaceAndPath("lethalbreed", path), Codec.LONG);
        }
    }

    /** Register the three types, at mod init; see {@code ContaminationManager.init}.
     *
     *  <p>The {@code values()} call is the whole method. Loading {@code PlagueDeadlines} does NOT initialise a
     *  nested enum, so without it the constants, and the registrations they carry, wait for the first
     *  {@link #set} of the session, by which time entities have already been read from disk. */
    public static void init() {
        Deadline.values();
    }

    /** Record a deadline in the live map and on the victim, so a reload finds it again. */
    public static void set(Deadline d, LivingEntity e, long due) {
        d.live.put(e, due);
        e.setAttached(d.saved, due);
    }

    /** Retire a deadline that has been reached or made irrelevant, from both halves. */
    public static void clear(Deadline d, LivingEntity e) {
        d.live.remove(e);
        e.removeAttached(d.saved);
    }

    /** What the victim carries on disk for one deadline, or {@code null}. For a rig that has to tell a
     *  restored deadline from a freshly rolled one; the mod itself reads the live map. */
    public static Long saved(Deadline d, LivingEntity e) {
        return e.getAttached(d.saved);
    }

    /** Refill the timer maps from what the victim carries. Called when a contaminated entity loads. */
    public static void reload(LivingEntity e) {
        for (Deadline d : Deadline.values()) {
            Long due = e.getAttached(d.saved);
            if (due != null) {
                d.live.put(e, due);
            }
        }
    }

    /** Strip the saved deadlines outright, for cure and death. Distinct from
     *  {@link ContaminationState#forgetTimers}, which only empties the maps: an unloading victim must keep its
     *  deadlines, a cured one must not, or its next infection would inherit them. */
    public static void strip(LivingEntity e) {
        for (Deadline d : Deadline.values()) {
            e.removeAttached(d.saved);
        }
    }
}
