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
 *       hides the skull for one tick), the HUD hearts/food read green client-side, and the victim takes the
 *       ramping plague pulse (health + player food).</li>
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

    private static final Identifier LATENT_SLOW_ID =
            Identifier.fromNamespaceAndPath("lethalbreed", "contam_latent_slow");

    private static final Set<LivingEntity> tracked = new HashSet<>();
    /** Server-tick of the next plague pulse per victim (transient; reseeded on load). */
    private static final java.util.Map<LivingEntity, Long> nextPulse = new java.util.HashMap<>();
    /** Server-tick of the next latent symptom-trigger roll per victim (transient; reseeded on load). */
    private static final java.util.Map<LivingEntity, Long> nextSymptomRoll = new java.util.HashMap<>();
    /** Server-tick at which the latent slow modifier should be removed per victim (transient). */
    private static final java.util.Map<LivingEntity, Long> latentSlowUntil = new java.util.HashMap<>();
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
                applyIcon(le);
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
        e.removeAttached(CONTAM);
        e.removeAttached(SYMPTOMATIC);
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

            // Milk-proof: keep the skull icon present (symptomatic only).
            if (e.getEffect(LethalBreedEffects.SUPER_CONTAMINATION) == null) {
                applyIcon(e);
            }

            // Slow plague pulse: every 5–10 real seconds (random per pulse) deal a small 0.1–0.5 chip to BOTH
            // health and (players) food — nothing else. Undead are wither-immune, so they carry the plague but
            // don't die of it; they only convert if killed some other way while affected.
            Long due = nextPulse.get(e);
            if (due == null) {
                nextPulse.put(e, t + rollIntervalTicks());
            } else if (t >= due) {
                float dmg = (float) (ContaminationConfig.contamDamageMin
                        + RNG.nextDouble() * (ContaminationConfig.contamDamageMax - ContaminationConfig.contamDamageMin));
                e.hurtServer(level, e.damageSources().wither(), dmg);
                if (e instanceof Player p) {
                    // Exhaustion drains food gradually: 4.0 exhaustion = 1 food point, so this removes ~dmg food.
                    p.getFoodData().addExhaustion(dmg * 4.0f);
                }
                nextPulse.put(e, t + rollIntervalTicks());
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
                applyIcon(e);
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
        removeLatentSlow(e);
        tracked.remove(e);
        nextPulse.remove(e);
        nextSymptomRoll.remove(e);
        latentSlowUntil.remove(e);
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

    private static void applyIcon(LivingEntity e) {
        e.addEffect(new MobEffectInstance(LethalBreedEffects.SUPER_CONTAMINATION,
                MobEffectInstance.INFINITE_DURATION, 0, false, true, true));
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
        applyIcon(e);
        nextSymptomRoll.remove(e);
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
