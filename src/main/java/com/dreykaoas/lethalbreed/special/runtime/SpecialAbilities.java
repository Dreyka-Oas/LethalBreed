package com.dreykaoas.lethalbreed.special.runtime;

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
        level.explode(z, z.getX(), z.getY() + 0.5, z.getZ(), 3.0f, Level.ExplosionInteraction.NONE);
        z.discard();
    }

    /** HURLEUR: hand the caller's target to nearby target-less smart zombies. */
    public static void hurl(SmartZombie sz, Zombie z, LivingEntity tgt, WorldAIContext ctx) {
        for (SmartZombie o : ctx.spatialGrid().queryRadius(z.getX(), z.getY(), z.getZ(), 24.0)) {
            if (o != sz && !o.hasTarget()) {
                o.entity().setTarget(tgt);
                o.pursuit().setTarget(tgt, tgt.getX(), tgt.getY(), tgt.getZ());
                SpecialBehavior.HURL_COUNT.incrementAndGet();
            }
        }
    }

    /** SOIGNEUR: grant regeneration to nearby living smart zombies. */
    public static void heal(SmartZombie sz, Zombie z, WorldAIContext ctx) {
        for (SmartZombie o : ctx.spatialGrid().queryRadius(z.getX(), z.getY(), z.getZ(), 8.0)) {
            if (o != sz && o.entity().isAlive()) {
                o.entity().addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0, false, false, true));
                SpecialBehavior.HEAL_COUNT.incrementAndGet();
            }
        }
    }

    /** NECROMANCIEN: summon 1–2 child zombies, capped against an already-dense local pack. */
    public static void summon(ServerLevel level, Zombie z, WorldAIContext ctx) {
        if (ctx.spatialGrid().queryRadius(z.getX(), z.getY(), z.getZ(), 12.0).size() > 40) {
            return;
        }
        int n = 1 + level.getRandom().nextInt(2);
        for (int i = 0; i < n; i++) {
            if (ChildSpawner.spawnNear(level, z, 2) != null) {
                SpecialBehavior.SUMMON_COUNT.incrementAndGet();
            }
        }
    }
}
