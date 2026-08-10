package com.dreykaoas.lethalbreed.mixin.client.render;

import com.dreykaoas.lethalbreed.client.LethalBreedClientConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Distance-culls plain zombies from rendering per the client config. Cooperative with Sodium: when
 * Sodium is present and adapt-mode is on, the cull distance is kept generous so we never fight
 * Sodium's own (more aggressive) culling. Only affects {@code minecraft:zombie}.
 */
@Environment(EnvType.CLIENT)
@Mixin(EntityRenderer.class)
public class EntityRendererMixin {

    // require = 0: purely presentational — see com.dreykaoas.lethalbreed.client.PresentationalMixinNotes.
    @Inject(require = 0, method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void lethalbreed$cullDistantZombies(Entity entity, Frustum frustum,
                                                double camX, double camY, double camZ,
                                                CallbackInfoReturnable<Boolean> cir) {
        if (entity.getType() != EntityType.ZOMBIE) {
            return;
        }
        LethalBreedClientConfig cfg = LethalBreedClientConfig.get();
        if (!cfg.enabled || !cfg.cullDistantZombies) {
            return;
        }
        if (entity.distanceToSqr(camX, camY, camZ) > cfg.effectiveCullDistanceSq()) {
            cir.setReturnValue(false);
        }
    }
}
