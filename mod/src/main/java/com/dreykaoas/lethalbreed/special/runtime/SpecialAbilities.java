package com.dreykaoas.lethalbreed.special.runtime;

import com.dreykaoas.lethalbreed.config.domain.PackConfig;
import com.dreykaoas.lethalbreed.config.domain.SpecialVariantConfig;
import com.dreykaoas.lethalbreed.pack.PackState;
import com.dreykaoas.lethalbreed.dimension.WorldAIContext;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import com.dreykaoas.lethalbreed.special.SpecialBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;

/**
 * Per-activation behaviours for ACTIVE specials. Each method self-contains one ability; the dispatch in
 * {@link SpecialBehavior} decides which fires and owns cooldown gating. Dev counters live on SpecialBehavior.
 */
public final class SpecialAbilities {
    private SpecialAbilities() {}

    /** BOMBEUR: explode and remove self when close to the target. */
    public static void bomb(ServerLevel level, Zombie z) {
        level.explode(z, z.getX(), z.getY() + 0.5, z.getZ(),
                (float) SpecialVariantConfig.specialBombeurPower, Level.ExplosionInteraction.NONE);
        z.discard();
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
