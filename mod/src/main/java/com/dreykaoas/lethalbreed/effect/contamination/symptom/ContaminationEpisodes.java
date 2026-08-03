package com.dreykaoas.lethalbreed.effect.contamination.symptom;

import com.dreykaoas.lethalbreed.effect.contamination.ContaminationRoll;
import com.dreykaoas.lethalbreed.effect.contamination.ContaminationState;

import com.dreykaoas.lethalbreed.config.domain.ContaminationConfig;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * The three independent random symptomatic afflictions (slow / no-jump / weak-strike), each on its own timer via
 * a transient attribute modifier (no effect icon, no particles). Also hosts the shared {@link EpisodeTimers}
 * record used by {@link ContaminationHallucination} for its own single flare timer.
 */
public final class ContaminationEpisodes {
    private ContaminationEpisodes() {}

    /** The three random symptomatic afflictions. Each flares on its own timer via a transient attribute modifier
     *  (no effect icon, no particles). {@code amount()} is the fraction removed; JUMP is a special-case full block. */
    public enum Episode {
        SLOW(Attributes.MOVEMENT_SPEED, "contam_slow",
                () -> ContaminationConfig.contamSlowAmount,
                () -> ContaminationConfig.contamSlowDurMinSec, () -> ContaminationConfig.contamSlowDurMaxSec,
                () -> ContaminationConfig.contamSlowGapMinSec, () -> ContaminationConfig.contamSlowGapMaxSec),
        NO_JUMP(Attributes.JUMP_STRENGTH, "contam_nojump",
                () -> 1.0, // remove 100% of jump strength → cannot jump
                () -> ContaminationConfig.contamNoJumpDurMinSec, () -> ContaminationConfig.contamNoJumpDurMaxSec,
                () -> ContaminationConfig.contamNoJumpGapMinSec, () -> ContaminationConfig.contamNoJumpGapMaxSec),
        WEAK(Attributes.ATTACK_DAMAGE, "contam_weak",
                () -> ContaminationConfig.contamWeakAmount,
                () -> ContaminationConfig.contamWeakDurMinSec, () -> ContaminationConfig.contamWeakDurMaxSec,
                () -> ContaminationConfig.contamWeakGapMinSec, () -> ContaminationConfig.contamWeakGapMaxSec);

        final net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr;
        final Identifier id;
        final java.util.function.DoubleSupplier amount, durMin, durMax, gapMin, gapMax;

        Episode(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, String name,
                java.util.function.DoubleSupplier amount, java.util.function.DoubleSupplier durMin,
                java.util.function.DoubleSupplier durMax, java.util.function.DoubleSupplier gapMin,
                java.util.function.DoubleSupplier gapMax) {
            this.attr = attr;
            this.id = Identifier.fromNamespaceAndPath("lethalbreed", name);
            this.amount = amount;
            this.durMin = durMin; this.durMax = durMax; this.gapMin = gapMin; this.gapMax = gapMax;
        }
    }

    /** Per-victim episode timing: server-tick the current flare ends (0 = inactive) and the tick the next starts.
     *  Shared shape reused by {@link ContaminationHallucination} for its single flare timer. */
    public static final class EpisodeTimers {
        public long activeUntil;
        public long nextStart;
    }

    /** episode timers keyed by victim then episode; transient (reseeded on load, cleared on cure/death). */
    private static final java.util.Map<LivingEntity, java.util.EnumMap<Episode, EpisodeTimers>> episodes =
            new java.util.HashMap<>();

    /** Drive the three independent symptomatic episodes for one victim. Higher intensity (mult) makes flares
     *  stronger and longer and the gaps between them shorter. Seeded lazily so a freshly-symptomatic victim gets
     *  its first flare after a normal gap rather than instantly. */
    public static void tickEpisodes(LivingEntity e, long t, double mult) {
        java.util.EnumMap<Episode, EpisodeTimers> map =
                episodes.computeIfAbsent(e, k -> new java.util.EnumMap<>(Episode.class));
        for (Episode ep : Episode.values()) {
            EpisodeTimers st = map.computeIfAbsent(ep, k -> {
                EpisodeTimers s = new EpisodeTimers();
                s.nextStart = t + rollGap(ep, mult); // first flare after a full gap
                return s;
            });
            if (st.activeUntil > 0) {
                if (t >= st.activeUntil) {            // flare ends
                    removeEpisode(e, ep);
                    st.activeUntil = 0;
                    st.nextStart = t + rollGap(ep, mult);
                }
            } else if (t >= st.nextStart) {           // flare starts
                applyEpisode(e, ep, mult);
                st.activeUntil = t + rollDur(ep, mult);
            }
        }
    }

    /** Flare duration in ticks, scaled UP by intensity (longer at higher levels). */
    private static long rollDur(Episode ep, double mult) {
        return ContaminationState.rollWindow(ep.durMin.getAsDouble(), ep.durMax.getAsDouble(), mult);
    }

    /** Gap-between-flares in ticks, scaled DOWN by intensity (more frequent at higher levels). */
    private static long rollGap(Episode ep, double mult) {
        return ContaminationState.rollWindow(ep.gapMin.getAsDouble(), ep.gapMax.getAsDouble(),
                ContaminationRoll.intensityFactor(mult));
    }

    /** Turn an episode ON: add its transient attribute modifier. Fraction removed is scaled by intensity, capped
     *  at 90% for SLOW/WEAK so the victim isn't fully frozen/harmless; JUMP always removes 100%. */
    private static void applyEpisode(LivingEntity e, Episode ep, double mult) {
        AttributeInstance inst = e.getAttribute(ep.attr);
        double amt = ep.amount.getAsDouble();
        if (inst == null || amt <= 0.0) {
            return;
        }
        if (ep != Episode.NO_JUMP) {
            amt = Math.min(ContaminationConfig.contamEpisodeCap, amt * mult);
        }
        inst.addOrUpdateTransientModifier(new AttributeModifier(
                ep.id, -amt, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    /** Turn an episode OFF: strip its modifier if present. */
    private static void removeEpisode(LivingEntity e, Episode ep) {
        AttributeInstance inst = e.getAttribute(ep.attr);
        if (inst != null) {
            inst.removeModifier(ep.id);
        }
    }

    /** Strip any active episode modifiers and forget a victim's episode timers (on cure/death). */
    public static void clearEpisodes(LivingEntity e) {
        if (episodes.remove(e) != null) {
            for (Episode ep : Episode.values()) {
                removeEpisode(e, ep);
            }
        }
    }

    /** Drop all victims' episode timers at once (server stop). The world is going away, so the transient
     *  attribute modifiers go with it — this only releases the map's references to dead entities. */
    public static void clearAllVictims() {
        episodes.clear();
    }
}
