package com.dreykaoas.lethalbreed.effect;

import com.dreykaoas.lethalbreed.config.domain.ContaminationConfig;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Drives the Super Contamination plague. Source of truth is a PERSISTENT integer attachment (the contamination
 * age, ticks). While it's > 0 the victim takes ramping wither damage to death, players lose hunger faster and
 * faster, and the effect icon is re-applied every tick — so drinking milk (which strips effects) only hides
 * the skull for one tick. The ONLY cure is staying crouched: each check has a tiny random chance (5–8%) to
 * shake it. On death the victim (any non-zombie mob) transforms into a zombie.
 */
public final class ContaminationManager {
    private ContaminationManager() {}

    /** Contamination age in ticks; >0 means contaminated. Persistent → survives reload, milk can't clear it. */
    public static final AttachmentType<Integer> CONTAM = AttachmentRegistry.createPersistent(
            Identifier.fromNamespaceAndPath("lethalbreed", "contamination"), Codec.INT);

    private static final Set<LivingEntity> tracked = new HashSet<>();
    private static final Random RNG = new Random();

    // Dev instrumentation (headless harness).
    public static final java.util.concurrent.atomic.AtomicInteger INFECT_COUNT = new java.util.concurrent.atomic.AtomicInteger();
    public static final java.util.concurrent.atomic.AtomicInteger ZOMBIFY_COUNT = new java.util.concurrent.atomic.AtomicInteger();

    public static void init() {}

    /** Infect a victim (called from the zombie-hit hook). No-op if already contaminated or it's a zombie. */
    public static void contaminate(LivingEntity e) {
        if (!ContaminationConfig.contaminationEnabled || e instanceof Zombie || age(e) > 0) {
            return;
        }
        e.setAttached(CONTAM, 1);
        applyIcon(e);
        tracked.add(e);
        INFECT_COUNT.incrementAndGet();
    }

    /** Re-track a contaminated entity after chunk reload (its attachment persists, the in-memory set doesn't). */
    public static void onLoad(Entity e) {
        if (e instanceof LivingEntity le && age(le) > 0) {
            tracked.add(le);
            applyIcon(le);
        }
    }

    /** Death → zombify any contaminated non-zombie mob (players just die). */
    public static void onDeath(LivingEntity e, ServerLevel level) {
        if (age(e) <= 0) {
            return;
        }
        tracked.remove(e);
        e.removeAttached(CONTAM);
        if (e instanceof Zombie || e instanceof Player) {
            return;
        }
        EntityType.ZOMBIE.spawn(level, e.blockPosition(), EntitySpawnReason.MOB_SUMMONED);
        ZOMBIFY_COUNT.incrementAndGet();
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

            // Milk-proof: keep the skull icon present.
            if (e.getEffect(LethalBreedEffects.SUPER_CONTAMINATION) == null) {
                applyIcon(e);
            }

            c++;
            e.setAttached(CONTAM, c);

            // Ramping wither damage to death.
            if (t % Math.max(1, ContaminationConfig.contamDamageInterval) == 0) {
                float dmg = (float) Math.min(ContaminationConfig.contamDamageCap,
                        ContaminationConfig.contamDamageBase + c * ContaminationConfig.contamDamageRamp);
                e.hurtServer(level, e.damageSources().wither(), dmg);
            }

            // Progressive hunger drain for players.
            if (e instanceof Player p && t % Math.max(1, ContaminationConfig.contamHungerInterval) == 0) {
                FoodData fd = p.getFoodData();
                int drain = 1 + c / 200;
                fd.setFoodLevel(Math.max(0, fd.getFoodLevel() - drain));
            }
        }
    }

    private static void cure(LivingEntity e) {
        e.removeAttached(CONTAM);
        e.removeEffect(LethalBreedEffects.SUPER_CONTAMINATION);
        tracked.remove(e);
    }

    private static void applyIcon(LivingEntity e) {
        e.addEffect(new MobEffectInstance(LethalBreedEffects.SUPER_CONTAMINATION,
                MobEffectInstance.INFINITE_DURATION, 0, false, true, true));
    }

    private static int age(LivingEntity e) {
        Integer v = e.getAttached(CONTAM);
        return v == null ? 0 : v;
    }
}
