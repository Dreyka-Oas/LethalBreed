package com.dreykaoas.lethalbreed.mixin.client;

import com.dreykaoas.lethalbreed.effect.LethalBreedEffects;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Enderman ambient particles are vanilla PORTAL (purple). When the enderman is INFECTED (carries the
 * Super Contamination effect, which is synced to tracking clients), swap them for black dust particles.
 * Uninfected endermen keep the vanilla purple swirl.
 */
@Mixin(EnderMan.class)
public abstract class EndermanParticleMixin {

    // Black, scale 1.0. new DustParticleOptions(packedRGB, scale).
    private static final DustParticleOptions LETHALBREED$BLACK =
            new DustParticleOptions(0x000000, 1.0f);

    @Redirect(
            method = "aiStep",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
    private void lethalbreed$blackParticlesWhenInfected(Level level, ParticleOptions particle,
                                                        double x, double y, double z,
                                                        double xd, double yd, double zd) {
        LivingEntity self = (LivingEntity) (Object) this;
        // Black dust when the enderman is contaminated (carries the effect, synced to the client so it reads
        // correctly render-side). Uninfected endermen keep the vanilla purple swirl.
        boolean infected = LethalBreedEffects.SUPER_CONTAMINATION != null
                && self.hasEffect(LethalBreedEffects.SUPER_CONTAMINATION);
        if (infected) {
            level.addParticle(LETHALBREED$BLACK, x, y, z, xd, yd, zd);
        } else {
            level.addParticle(particle, x, y, z, xd, yd, zd);
        }
    }
}
