package com.dreykaoas.lethalbreed.special.runtime;

import com.dreykaoas.lethalbreed.config.domain.PackConfig;
import com.dreykaoas.lethalbreed.config.domain.SpecialVariantConfig;
import com.dreykaoas.lethalbreed.effect.ContaminationManager;
import com.dreykaoas.lethalbreed.pack.PackState;
import com.dreykaoas.lethalbreed.dimension.WorldAIContext;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import com.dreykaoas.lethalbreed.special.SpecialBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Per-activation behaviours for ACTIVE specials. Each method self-contains one ability; the dispatch in
 * {@link SpecialBehavior} decides which fires and owns cooldown gating. Dev counters live on SpecialBehavior.
 */
public final class SpecialAbilities {
    private SpecialAbilities() {}

    /**
     * BOMBEUR: burst, then splatter everything in the wider gore ring with infectious status effects.
     *
     * <p>The blast is only half of it. The splatter ring reaches {@code specialBombeurSplatterMul} times
     * further, so retreating out of lethal range still leaves a victim inside the gore — distance buys
     * hit points, not a clean escape.
     *
     * @param fuseTicks how long this Bombeur swelled; drives both the power and the splatter intensity
     */
    public static void bomb(ServerLevel level, Zombie z, int fuseTicks) {
        double ratio = BombeurBlast.ratioOf(fuseTicks);
        double power = BombeurBlast.powerFor(ratio);
        double splatR = BombeurBlast.splatterRadius(power);
        double cx = z.getX(), cy = z.getY() + 0.5, cz = z.getZ();

        // Gather BEFORE the explosion: it kills and flings victims, and anyone it launched out of the ring
        // was still standing in the gore at the moment it burst.
        List<LivingEntity> caught = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(cx - splatR, cy - splatR, cz - splatR, cx + splatR, cy + splatR, cz + splatR),
                e -> e != z && e.isAlive() && !(e instanceof Zombie));
        RandomSource rng = z.getRandom();

        level.explode(z, cx, cy, cz, (float) power, Level.ExplosionInteraction.NONE);
        z.discard();

        for (LivingEntity victim : caught) {
            // The AABB is a box; the ring is a sphere. Re-measure so corners don't get splattered.
            double intensity = BombeurBlast.intensity(ratio, Math.sqrt(victim.distanceToSqr(cx, cy, cz)), splatR);
            if (intensity > 0.0) {
                splatter(victim, intensity, rng);
            }
        }
    }

    /**
     * Apply one victim's share of the gore. Zombies are filtered out by the caller: they are the vector, not
     * the victim — and {@code contaminate()} refuses them anyway.
     */
    private static void splatter(LivingEntity victim, double intensity, RandomSource rng) {
        victim.addEffect(new MobEffectInstance(MobEffects.NAUSEA, BombeurBlast.nauseaTicks(intensity), 0));
        victim.addEffect(new MobEffectInstance(MobEffects.POISON,
                BombeurBlast.poisonTicks(intensity), BombeurBlast.poisonAmp(intensity)));
        victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,
                BombeurBlast.slowTicks(intensity), BombeurBlast.slowAmp(intensity)));
        int blind = BombeurBlast.blindTicks(intensity);
        if (blind > 0) {
            victim.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, blind, 0));
        }
        if (rng.nextDouble() < BombeurBlast.infectChance(intensity)) {
            ContaminationManager.contaminate(victim);
        }
    }

    /** HURLEUR: hand the caller's target to nearby target-less smart zombies. */
    public static void hurl(SmartZombie sz, Zombie z, LivingEntity tgt, WorldAIContext ctx) {
        for (SmartZombie o : ctx.spatialGrid().queryRadius(z.getX(), z.getY(), z.getZ(),
                SpecialVariantConfig.specialHurleurRadius)) {
            if (o != sz && !o.hasTarget()) {
                o.entity().setTarget(tgt);
                o.pursuit().setTarget(tgt, tgt.getX(), tgt.getY(), tgt.getZ());
                SpecialBehavior.HURL_COUNT.incrementAndGet();
            }
        }
    }

    /** SOIGNEUR: grant regeneration to nearby living smart zombies. */
    public static void heal(SmartZombie sz, Zombie z, WorldAIContext ctx) {
        for (SmartZombie o : ctx.spatialGrid().queryRadius(z.getX(), z.getY(), z.getZ(),
                SpecialVariantConfig.specialSoigneurRadius)) {
            if (o != sz && o.entity().isAlive()) {
                o.entity().addEffect(new MobEffectInstance(MobEffects.REGENERATION,
                        SpecialVariantConfig.specialSoigneurRegenTicks, SpecialVariantConfig.specialSoigneurRegenAmp,
                        false, false, true));
                SpecialBehavior.HEAL_COUNT.incrementAndGet();
            }
        }
    }

    /** NECROMANCIEN: summon child zombies, capped against an already-dense local pack. */
    public static void summon(SmartZombie sz, ServerLevel level, Zombie z, WorldAIContext ctx) {
        if (ctx.spatialGrid().queryRadius(z.getX(), z.getY(), z.getZ(),
                SpecialVariantConfig.specialNecromancienDensityRadius).size()
                > SpecialVariantConfig.specialNecromancienDensityCap) {
            return;
        }
        // A summoner inside a pack is also capped by that pack's size. The density cap alone counts a radius,
        // not a roster: a Necromancer marching with its pack keeps summoning until 40 zombies stand within 12
        // blocks, and since this mod never despawns anything, the pack grows without bound for the rest of the
        // world's life.
        PackState pack = ctx.packManager().get(sz.pursuit().pack().packId());
        if (pack != null && pack.totalMembers() >= PackConfig.packMaxSize) {
            return;
        }
        int min = SpecialVariantConfig.specialNecromancienMinChildren;
        int max = Math.max(min, SpecialVariantConfig.specialNecromancienMaxChildren);
        int n = min + level.getRandom().nextInt(max - min + 1);
        int spread = SpecialVariantConfig.specialNecromancienSpread;
        for (int i = 0; i < n; i++) {
            if (ChildSpawner.spawnNear(level, z, spread) != null) {
                SpecialBehavior.SUMMON_COUNT.incrementAndGet();
            }
        }
    }
}
