package com.example.examplebreed;

import oas.dreyka.lethalbreed.api.variant.VariantBehavior;
import oas.dreyka.lethalbreed.api.variant.VariantContext;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;

/** Slow on the zombie itself, and a chill on everything near it once it has something to hunt. */
final class FrostbiteBehaviour implements VariantBehavior {

    @Override
    public void onSpawn(Zombie zombie) {
        zombie.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, -1, 0, false, false));
    }

    @Override
    public void onUnassign(Zombie zombie) {
        zombie.removeEffect(MobEffects.SLOWNESS);
    }

    @Override
    public void tick(VariantContext ctx) {
        if (ctx.target() == null || !ctx.ready()) {
            return;
        }
        int ticks = FrostbiteConfig.frostbiteSlowSeconds * 20;
        for (LivingEntity prey : ctx.level().getEntitiesOfClass(LivingEntity.class,
                ctx.zombie().getBoundingBox().inflate(FrostbiteConfig.frostbiteRadius))) {
            if (prey != ctx.zombie()) {
                prey.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 1));
            }
        }
        ctx.resetCooldown();
    }
}
