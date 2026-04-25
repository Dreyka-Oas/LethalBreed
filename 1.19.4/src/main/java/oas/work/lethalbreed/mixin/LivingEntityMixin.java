package oas.work.lethalbreed.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "hasLineOfSight", at = @At("HEAD"), cancellable = true)
    private void lethalVision(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object)this instanceof Zombie z)) return;
        if (z.getTags().contains("lethal_no_head")) {
            cir.setReturnValue(false); 
            return;
        }

        LivingEntity self = (LivingEntity)(Object)this;
        Vec3 start = new Vec3(self.getX(), self.getEyeY(), self.getZ());
        Vec3 end = new Vec3(entity.getX(), entity.getEyeY(), entity.getZ());
        if (end.distanceTo(start) > 128.0d) return;

        var world = self.getLevel();
        BlockHitResult res = world.clip(new ClipContext(
            start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, self
        ));

        if (res.getType() == HitResult.Type.BLOCK) {
            var state = world.getBlockState(res.getBlockPos());
            if (state.getLightBlock(world, res.getBlockPos()) < 15 || !state.isCollisionShapeFullBlock(world, res.getBlockPos())) {
                cir.setReturnValue(true);
            }
        }
    }
}







