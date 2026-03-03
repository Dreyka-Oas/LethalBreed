/**
 * Project: Lethal Breed
 * Responsibility: Enhanced Entity Vision Mixin
 * License: O.A.S - MS-RSL (Microsoft Reference Source License)
 * Copyright (c) 2026 O.A.S (Optimization & Quality). All rights reserved.
 */
package oas.work.lethalbreed.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "canSee", at = @At("HEAD"), cancellable = true)
    private void lethalVision(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object)this instanceof ZombieEntity z)) return;
        if (z.getCommandTags().contains("lethal_no_head")) {
            cir.setReturnValue(false); return;
        }

        LivingEntity self = (LivingEntity)(Object)this;
        Vec3d start = new Vec3d(self.getX(), self.getEyeY(), self.getZ());
        Vec3d end = new Vec3d(entity.getX(), entity.getEyeY(), entity.getZ());
        if (end.distanceTo(start) > 128.0d) return;

        // Perform a raycast that ignores translucent blocks (opacity < 15)
        var world = self.getEntityWorld();
        BlockHitResult res = world.raycast(new RaycastContext(
            start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, self
        ));

        if (res.getType() == HitResult.Type.BLOCK) {
            var state = world.getBlockState(res.getBlockPos());
            // If the blocking block is transparent, force 'canSee' to true
            if (state.getOpacity() < 15 || !state.isFullCube(world, res.getBlockPos())) {
                cir.setReturnValue(true);
            }
        }
    }
}
