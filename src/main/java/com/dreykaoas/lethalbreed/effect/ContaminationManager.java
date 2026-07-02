package com.dreykaoas.lethalbreed.effect;

import com.dreykaoas.lethalbreed.config.domain.ContaminationConfig;

import com.mojang.serialization.Codec;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Drives the Super Contamination plague. Source of truth is a PERSISTENT integer attachment (the contamination
 * age, ticks) plus a PERSISTENT boolean flag (symptomatic yet?).
 *
 * <p>Two stages:
 * <ul>
 *   <li><b>Latent</b> (age &gt; 0, not symptomatic): nothing is shown — no HUD tint, no skull icon, no
 *       particles, no plague damage. The victim never knows. The only effect is a brief, particleless slow
 *       applied once at the moment of infection (a short movement-speed attribute modifier). Every 5–10
 *       in-game days a roll (2–10% chance) may surface the symptoms.</li>
 *   <li><b>Symptomatic</b> (age &gt; 0, symptomatic): the effect icon is re-applied every tick (so milk only
 *       hides the skull for one tick), the HUD hearts/food read green client-side, the victim takes the
 *       ramping plague pulse (health + player food), and three independent random episodes flare on their own
 *       timers: a movement slow, a no-jump lock, and a weak-strike (all particleless attribute modifiers).</li>
 * </ul>
 *
 * <p>The ONLY cure is staying crouched: each check has a tiny random chance (5–8%) to shake it. On death the
 * victim simply dies — the plague state is cleared; a humanoid may reanimate.
 */
public final class ContaminationManager {
    private ContaminationManager() {}

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

    private static final Identifier LATENT_SLOW_ID =
            Identifier.fromNamespaceAndPath("lethalbreed", "contam_latent_slow");

    /** The three random symptomatic afflictions. Each flares on its own timer via a transient attribute modifier
     *  (no effect icon, no particles). {@code amount()} is the fraction removed; JUMP is a special-case full block. */
    private enum Episode {
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

    /** Per-victim episode timing: server-tick the current flare ends (0 = inactive) and the tick the next starts. */
    private static final class EpisodeTimers {
        long activeUntil;
        long nextStart;
    }

    /** episode timers keyed by victim then episode; transient (reseeded on load, cleared on cure/death). */
    private static final java.util.Map<LivingEntity, java.util.EnumMap<Episode, EpisodeTimers>> episodes =
            new java.util.HashMap<>();

    /** Zombie-vision hallucination flare timer per victim; transient (reseeded on load, cleared on cure/death). */
    private static final java.util.Map<LivingEntity, EpisodeTimers> hallucTimers = new java.util.HashMap<>();

    private static final Set<LivingEntity> tracked = new HashSet<>();
    /** Server-tick of the next plague pulse per victim (transient; reseeded on load). */
    private static final java.util.Map<LivingEntity, Long> nextPulse = new java.util.HashMap<>();
    /** Server-tick of the next latent symptom-trigger roll per victim (transient; reseeded on load). */
    private static final java.util.Map<LivingEntity, Long> nextSymptomRoll = new java.util.HashMap<>();
    /** Server-tick at which the latent slow modifier should be removed per victim (transient). */
    private static final java.util.Map<LivingEntity, Long> latentSlowUntil = new java.util.HashMap<>();
    /** Server-tick of the next level-up roll per victim (transient; reseeded on load). */
    private static final java.util.Map<LivingEntity, Long> nextEvolveRoll = new java.util.HashMap<>();
    private static final Random RNG = new Random();
    private static final boolean DEV = FabricLoader.getInstance().isDevelopmentEnvironment();

    // Dev instrumentation (headless harness).
    public static final java.util.concurrent.atomic.AtomicInteger INFECT_COUNT = new java.util.concurrent.atomic.AtomicInteger();
    public static final java.util.concurrent.atomic.AtomicInteger DEATH_COUNT = new java.util.concurrent.atomic.AtomicInteger();

    public static void init() {}

    /** Infect a victim (called from the zombie-hit hook). No-op if already contaminated or it's a zombie.
     *  Starts LATENT: nothing visible, no plague damage — only a brief particleless slow right now. */
    public static void contaminate(LivingEntity e) {
        if (!ContaminationConfig.contaminationEnabled || e instanceof Zombie || age(e) > 0) {
            return;
        }
        e.setAttached(CONTAM, 1);
        e.setAttached(SYMPTOMATIC, false);
        tracked.add(e);
        applyLatentSlow(e);
        INFECT_COUNT.incrementAndGet();
    }

    /** Re-track a contaminated entity after chunk reload (its attachment persists, the in-memory set doesn't).
     *  Only re-show the icon if it had already turned symptomatic. */
    public static void onLoad(Entity e) {
        if (e instanceof LivingEntity le && age(le) > 0) {
            tracked.add(le);
            if (symptomatic(le)) {
                applyIcon(le, level(le) - 1);
            }
        }
    }

    /** Death of a contaminated victim: clear the plague state, then reanimate as a zombie if it was a humanoid. */
    public static void onDeath(LivingEntity e, ServerLevel level) {
        if (age(e) <= 0) {
            return;
        }
        tracked.remove(e);
        nextPulse.remove(e);
        nextSymptomRoll.remove(e);
        latentSlowUntil.remove(e);
        nextEvolveRoll.remove(e);
        clearEpisodes(e);
        hallucTimers.remove(e);
        e.removeEffect(LethalBreedEffects.ZOMBIE_VISION);
        e.removeAttached(CONTAM);
        e.removeAttached(SYMPTOMATIC);
        e.removeAttached(LEVEL);
        e.removeAttached(INTENSITY);
        DEATH_COUNT.incrementAndGet();
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
            level.addFreshEntity(z);
        }
    }

    /** A humanoid the plague can raise: players and the bipedal mob families (villagers, piglins, illagers). */
    private static boolean isHumanoid(LivingEntity e) {
        return e instanceof Player
                || e instanceof net.minecraft.world.entity.npc.villager.AbstractVillager
                || e instanceof net.minecraft.world.entity.monster.piglin.AbstractPiglin
                || e instanceof net.minecraft.world.entity.monster.illager.AbstractIllager
                || e instanceof net.minecraft.world.entity.monster.Witch;
    }

    public static void tick(MinecraftServer server) {
        if (!ContaminationConfig.contaminationEnabled || tracked.isEmpty()) {
            return;
        }
        long t = server.getTickCount();
        for (LivingEntity e : new HashSet<>(tracked)) {
            if (e == null || e.isRemoved() || !e.isAlive() || !(e.level() instanceof ServerLevel level)) {
                tracked.remove(e);
                continue;
            }
            int c = age(e);
            if (c <= 0) {
                cure(e);
                continue;
            }

            // Cure: only by staying crouched; tiny random chance per check.
            if (e.isCrouching() && t % Math.max(1, ContaminationConfig.contamCureCheckTicks) == 0) {
                double pct = ContaminationConfig.contamCureMinPct
                        + RNG.nextDouble() * (ContaminationConfig.contamCureMaxPct - ContaminationConfig.contamCureMinPct);
                if (RNG.nextDouble() * 100.0 < pct) {
                    cure(e);
                    continue;
                }
            }

            c++;
            e.setAttached(CONTAM, c);

            // Dev-only visual: since the latent stage is invisible by design, show a debug action-bar tag so a
            // developer can confirm infection state in-game. Never shown outside a dev environment.
            if (DEV) {
                showDevIndicator(e);
            }

            if (!symptomatic(e)) {
                tickLatent(e, t);
                continue;
            }

            // Milk / "/effect clear" removes the skull effect → this now CURES the plague outright (no lingering
            // latent stage): the contamination age is wiped so symptoms can't resurface later.
            int lvl = level(e);
            int wantAmp = Math.max(0, lvl - 1);
            MobEffectInstance cur = e.getEffect(LethalBreedEffects.SUPER_CONTAMINATION);
            if (cur == null) {
                cure(e);
                continue;
            }
            if (cur.getAmplifier() != wantAmp) {
                applyIcon(e, wantAmp);
            }

            // A player who can't take normal damage (Creative / Spectator) keeps the plague — icon, level, everything
            // stays — but none of its active effects fire: no health/food pulse, no episodes, no hallucination, no
            // evolution. The moment they return to Survival/Adventure the symptoms resume from where they were.
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) {
                continue;
            }

            // Level-up roll: every 1–2 in-game days a chance to climb toward maxLevel (recomputes intensity).
            tickEvolve(e, t);

            double mult = intensity(e); // per-victim intensity for the current level (1.0 at level 1 baseline)

            // Slow plague pulse: every 5–10 real seconds (random per pulse) shave a small chip off BOTH health and
            // (players) food. Higher level → bigger chip (×mult). Zombies are never tracked, so it can't chip its
            // own kind. Only the final, fatal chip goes through the vanilla damage pipeline (death/reanimation).
            Long due = nextPulse.get(e);
            if (due == null) {
                nextPulse.put(e, t + rollIntervalTicks());
            } else if (t >= due) {
                float dmg = (float) ((ContaminationConfig.contamDamageMin
                        + RNG.nextDouble() * (ContaminationConfig.contamDamageMax - ContaminationConfig.contamDamageMin))
                        * mult);
                float next = e.getHealth() - dmg;
                if (next > 0.0f) {
                    e.setHealth(next);
                } else {
                    e.hurtServer(level, e.damageSources().magic(), Float.MAX_VALUE);
                }
                if (e instanceof Player p) {
                    // Exhaustion drains food gradually: 4.0 exhaustion = 1 food point, so this removes ~dmg food.
                    p.getFoodData().addExhaustion(dmg * 4.0f);
                }
                nextPulse.put(e, t + rollIntervalTicks());
            }

            // Random episodic afflictions (slow / no-jump / weak-strike) — each on its own timer, scaled by mult.
            tickEpisodes(e, t, mult);

            // Zombie-vision hallucination — a fourth episode on its own random timer. Applies/removes the transient
            // ZOMBIE_VISION effect; the victim's client reads it to draw other players as zombies.
            tickHallucination(e, t, mult);
        }
    }

    /** Drive the zombie-vision hallucination flare for one victim: OFF between flares, ON (ZOMBIE_VISION applied)
     *  for a random duration, then a random gap. Duration scales up / gap scales down with intensity, like episodes. */
    private static void tickHallucination(LivingEntity e, long t, double mult) {
        EpisodeTimers st = hallucTimers.computeIfAbsent(e, k -> {
            EpisodeTimers s = new EpisodeTimers();
            s.nextStart = t + rollHallucGap(mult); // first flare after a full gap
            return s;
        });
        if (st.activeUntil > 0) {
            if (t >= st.activeUntil) {                       // flare ends
                e.removeEffect(LethalBreedEffects.ZOMBIE_VISION);
                st.activeUntil = 0;
                st.nextStart = t + rollHallucGap(mult);
            }
        } else if (t >= st.nextStart) {                      // flare starts
            e.addEffect(new MobEffectInstance(LethalBreedEffects.ZOMBIE_VISION,
                    MobEffectInstance.INFINITE_DURATION, 0, false, false, false));
            st.activeUntil = t + rollHallucDur(mult);
        }
    }

    private static long rollHallucDur(double mult) {
        return rollWindow(ContaminationConfig.contamHallucDurMinSec, ContaminationConfig.contamHallucDurMaxSec, mult);
    }

    private static long rollHallucGap(double mult) {
        return rollWindow(ContaminationConfig.contamHallucGapMinSec, ContaminationConfig.contamHallucGapMaxSec,
                1.0 / Math.max(1.0e-3, mult));
    }

    /** Drive the three independent symptomatic episodes for one victim. Higher intensity (mult) makes flares
     *  stronger and longer and the gaps between them shorter. Seeded lazily so a freshly-symptomatic victim gets
     *  its first flare after a normal gap rather than instantly. */
    private static void tickEpisodes(LivingEntity e, long t, double mult) {
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
        return rollWindow(ep.durMin.getAsDouble(), ep.durMax.getAsDouble(), mult);
    }

    /** Gap-between-flares in ticks, scaled DOWN by intensity (more frequent at higher levels). */
    private static long rollGap(Episode ep, double mult) {
        return rollWindow(ep.gapMin.getAsDouble(), ep.gapMax.getAsDouble(), 1.0 / Math.max(1.0e-3, mult));
    }

    /** Roll a random [minSec, maxSec] window as ticks × factor, honouring dev time-compression. */
    private static long rollWindow(double min, double max, double factor) {
        min = Math.max(0.0, min);
        max = Math.max(min, max);
        double sec = (min + RNG.nextDouble() * (max - min)) * factor;
        return Math.max(1L, Math.round(sec * 20.0 / devTimeScale()));
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
            amt = Math.min(0.9, amt * mult);
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
    private static void clearEpisodes(LivingEntity e) {
        if (episodes.remove(e) != null) {
            for (Episode ep : Episode.values()) {
                removeEpisode(e, ep);
            }
        }
    }

    /** Latent stage: no icon, no damage. Expire the one-shot slow, and every 5–10 in-game days roll a small
     *  chance to surface symptoms (which flips the victim into the visible/damaging stage). */
    private static void tickLatent(LivingEntity e, long t) {
        // Retire the brief infection slow once its window is up.
        Long slowEnd = latentSlowUntil.get(e);
        if (slowEnd != null && t >= slowEnd) {
            removeLatentSlow(e);
            latentSlowUntil.remove(e);
        }

        Long roll = nextSymptomRoll.get(e);
        if (roll == null) {
            nextSymptomRoll.put(e, t + rollSymptomIntervalTicks());
            return;
        }
        if (t >= roll) {
            double pct = ContaminationConfig.contamSymptomMinPct
                    + RNG.nextDouble() * (ContaminationConfig.contamSymptomMaxPct - ContaminationConfig.contamSymptomMinPct);
            if (RNG.nextDouble() * 100.0 < pct) {
                e.setAttached(SYMPTOMATIC, true);
                setLevel(e, 1); // enter symptomatic at level 1 (applies icon + seeds intensity)
                nextSymptomRoll.remove(e);
            } else {
                nextSymptomRoll.put(e, t + rollSymptomIntervalTicks());
            }
        }
    }

    /** Roll the next symptom-trigger delay in ticks, uniform in [minDays, maxDays] × 24000. */
    private static long rollSymptomIntervalTicks() {
        double min = Math.max(0.0, ContaminationConfig.contamSymptomMinDays);
        double max = Math.max(min, ContaminationConfig.contamSymptomMaxDays);
        return Math.max(1L, Math.round((min + RNG.nextDouble() * (max - min)) * 24000.0 / devTimeScale()));
    }

    /** Apply the brief, particleless latent slow as a transient movement-speed modifier. Its removal tick is
     *  computed from the current server tick so {@link #tickLatent} can strip it after the short window. */
    private static void applyLatentSlow(LivingEntity e) {
        AttributeInstance inst = e.getAttribute(Attributes.MOVEMENT_SPEED);
        if (inst == null || ContaminationConfig.contamLatentSlowAmount <= 0.0
                || !(e.level() instanceof ServerLevel level)) {
            return;
        }
        inst.addOrUpdateTransientModifier(new AttributeModifier(
                LATENT_SLOW_ID, -ContaminationConfig.contamLatentSlowAmount,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        long now = level.getServer().getTickCount();
        latentSlowUntil.put(e, now + Math.max(1, ContaminationConfig.contamLatentSlowTicks));
    }

    private static void removeLatentSlow(LivingEntity e) {
        AttributeInstance inst = e.getAttribute(Attributes.MOVEMENT_SPEED);
        if (inst != null) {
            inst.removeModifier(LATENT_SLOW_ID);
        }
    }

    /** Roll the next pulse delay in ticks, uniform in [contamIntervalMinSec, contamIntervalMaxSec] × 20. */
    private static long rollIntervalTicks() {
        double min = Math.max(0.0, ContaminationConfig.contamIntervalMinSec);
        double max = Math.max(min, ContaminationConfig.contamIntervalMaxSec);
        return Math.max(1L, Math.round((min + RNG.nextDouble() * (max - min)) * 20.0 / devTimeScale()));
    }

    /** Positive plague time-compression factor (dev command sets it; clamped to ≥ 1e-3 to avoid div-by-zero). */
    private static double devTimeScale() {
        return Math.max(1.0e-3, ContaminationConfig.contamDevTimeScale);
    }

    private static void cure(LivingEntity e) {
        e.removeAttached(CONTAM);
        e.removeAttached(SYMPTOMATIC);
        e.removeEffect(LethalBreedEffects.SUPER_CONTAMINATION);
        e.removeEffect(LethalBreedEffects.ZOMBIE_VISION);
        removeLatentSlow(e);
        clearEpisodes(e);
        hallucTimers.remove(e);
        e.removeAttached(LEVEL);
        e.removeAttached(INTENSITY);
        tracked.remove(e);
        nextPulse.remove(e);
        nextSymptomRoll.remove(e);
        latentSlowUntil.remove(e);
        nextEvolveRoll.remove(e);
    }

    /** Dev-only action-bar tag showing the plague stage. Players see it on themselves; other victims name-tag. */
    private static void showDevIndicator(LivingEntity e) {
        boolean sym = symptomatic(e);
        Component tag = Component.literal(sym ? "[INFECTÉ ✦ symptômes]" : "[INFECTÉ latent]")
                .withStyle(sym ? ChatFormatting.RED : ChatFormatting.GREEN);
        if (e instanceof net.minecraft.server.level.ServerPlayer p) {
            p.displayClientMessage(tag, true); // action bar
        } else {
            e.setCustomName(tag);
            e.setCustomNameVisible(true);
        }
    }

    private static boolean symptomatic(LivingEntity e) {
        Boolean v = e.getAttached(SYMPTOMATIC);
        return v != null && v;
    }

    private static void applyIcon(LivingEntity e, int amplifier) {
        // ambient=false, visible=false (NO swirling particles — our plague is silent), showIcon=true (skull only).
        // Amplifier mirrors (level-1) so the client scales its screen overlay from the effect it already syncs.
        e.addEffect(new MobEffectInstance(LethalBreedEffects.SUPER_CONTAMINATION,
                MobEffectInstance.INFINITE_DURATION, Math.max(0, amplifier), false, false, true));
    }

    // ---- Evolution (levels + per-victim intensity) ----

    /** Current plague level (1..maxLevel). 0/absent → treat as level 1 for a symptomatic victim. */
    private static int level(LivingEntity e) {
        Integer v = e.getAttached(LEVEL);
        return v == null || v < 1 ? 1 : Math.min(v, Math.max(1, ContaminationConfig.contamMaxLevel));
    }

    /** Per-victim intensity multiplier for the current level (≥ 1.0). Recomputed on each level-up. */
    private static double intensity(LivingEntity e) {
        Double v = e.getAttached(INTENSITY);
        return v == null || v < 1.0 ? 1.0 : v;
    }

    /** Roll a fresh per-victim intensity for a level: 1 + (level-1) × step × jitter, jitter random per victim. */
    private static void recomputeIntensity(LivingEntity e, int lvl) {
        double jitter = ContaminationConfig.contamLevelJitterMin
                + RNG.nextDouble() * (ContaminationConfig.contamLevelJitterMax - ContaminationConfig.contamLevelJitterMin);
        double mult = 1.0 + (lvl - 1) * ContaminationConfig.contamLevelStep * Math.max(0.0, jitter);
        e.setAttached(INTENSITY, Math.max(1.0, mult));
    }

    /** Set a victim's level (clamped), reroll its intensity, and re-apply the icon so the amplifier updates. */
    private static void setLevel(LivingEntity e, int lvl) {
        int max = Math.max(1, ContaminationConfig.contamMaxLevel);
        lvl = Math.max(1, Math.min(lvl, max));
        e.setAttached(LEVEL, lvl);
        recomputeIntensity(e, lvl);
        applyIcon(e, lvl - 1);
    }

    /** Level-up roll while symptomatic: every 1–2 in-game days a chance to climb one level toward the cap. */
    private static void tickEvolve(LivingEntity e, long t) {
        if (level(e) >= Math.max(1, ContaminationConfig.contamMaxLevel)) {
            return;
        }
        Long roll = nextEvolveRoll.get(e);
        if (roll == null) {
            nextEvolveRoll.put(e, t + rollEvolveIntervalTicks());
            return;
        }
        if (t >= roll) {
            double pct = ContaminationConfig.contamEvolveMinPct
                    + RNG.nextDouble() * (ContaminationConfig.contamEvolveMaxPct - ContaminationConfig.contamEvolveMinPct);
            if (RNG.nextDouble() * 100.0 < pct) {
                setLevel(e, level(e) + 1);
            }
            nextEvolveRoll.put(e, t + rollEvolveIntervalTicks());
        }
    }

    /** Next level-up roll delay in ticks, uniform in [minDays, maxDays] × 24000. */
    private static long rollEvolveIntervalTicks() {
        double min = Math.max(0.0, ContaminationConfig.contamEvolveMinDays);
        double max = Math.max(min, ContaminationConfig.contamEvolveMaxDays);
        return Math.max(1L, Math.round((min + RNG.nextDouble() * (max - min)) * 24000.0 / devTimeScale()));
    }

    /** True once a victim carries the plague (contamination age > 0). Used to let immune mobs still accept
     *  effects once infected. */
    public static boolean isContaminated(LivingEntity e) {
        return age(e) > 0;
    }

    /** True when the victim has already turned symptomatic (visible + damaging stage). */
    public static boolean isSymptomatic(LivingEntity e) {
        return symptomatic(e);
    }

    /** Dev tool: immediately surface symptoms on a contaminated victim (skips the 5–10 in-game-day roll), so
     *  the visible/damaging stage can be inspected on demand. No-op if the victim isn't contaminated. */
    public static void forceSymptomatic(LivingEntity e) {
        if (age(e) <= 0 || symptomatic(e)) {
            return;
        }
        e.setAttached(SYMPTOMATIC, true);
        setLevel(e, 1);
        nextSymptomRoll.remove(e);
    }

    /** Dev tool: jump a victim straight to a plague level (infect + surface symptoms first if needed). Clamped
     *  to [1, maxLevel]. Rerolls the per-victim intensity for that level. */
    public static void forceLevel(LivingEntity e, int lvl) {
        if (age(e) <= 0) {
            contaminate(e);
        }
        if (age(e) <= 0) {
            return; // contamination disabled or entity ineligible
        }
        if (!symptomatic(e)) {
            e.setAttached(SYMPTOMATIC, true);
            nextSymptomRoll.remove(e);
        }
        setLevel(e, lvl);
    }

    /** Current plague level for external/dev readout (1 while symptomatic, 0 if not symptomatic). */
    public static int plagueLevel(LivingEntity e) {
        return symptomatic(e) ? level(e) : 0;
    }

    /** Dev tool: clear the plague from a victim outright (public wrapper over the internal cure). */
    public static void clearPlague(LivingEntity e) {
        cure(e);
    }

    private static int age(LivingEntity e) {
        Integer v = e.getAttached(CONTAM);
        return v == null ? 0 : v;
    }
}
