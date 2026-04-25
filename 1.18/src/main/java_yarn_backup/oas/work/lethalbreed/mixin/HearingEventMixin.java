package oas.work.lethalbreed.mixin;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.event.GameEvent;
import oas.work.lethalbreed.ai.HearingLogic;
import oas.work.lethalbreed.ai.TemporaryBlockTracker;
import oas.work.lethalbreed.ai.builder.ObstructionAnalyzer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerWorld.class)
public abstract class HearingEventMixin {
    private static final GameEvent[] RELEVANT_EVENTS = new GameEvent[] {
        GameEvent.STEP,
        GameEvent.BLOCK_PLACE,
        GameEvent.BLOCK_DESTROY,
        GameEvent.EAT,
        GameEvent.HIT_GROUND,
        GameEvent.PROJECTILE_LAND,
        GameEvent.EXPLODE,
        GameEvent.SPLASH,
        GameEvent.ENTITY_PLACE
    };

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        ObstructionAnalyzer.onTick(((ServerWorld)(Object)this).getServer().getTicks());
        TemporaryBlockTracker.onTick((ServerWorld)(Object)this);
    }

    @Inject(method = "emitGameEvent(Lnet/minecraft/entity/Entity;Lnet/minecraft/world/event/GameEvent;Lnet/minecraft/util/math/BlockPos;)V", at = @At("HEAD"))
    private void onEmit(net.minecraft.entity.Entity entity, GameEvent event, BlockPos pos, CallbackInfo ci) {
        
        for (GameEvent relevant : RELEVANT_EVENTS) {
            if (event == relevant) {
                HearingLogic.onGameEvent((ServerWorld)(Object)this, event, Vec3d.ofCenter(pos), entity);
                return;
            }
        }
    }
}







