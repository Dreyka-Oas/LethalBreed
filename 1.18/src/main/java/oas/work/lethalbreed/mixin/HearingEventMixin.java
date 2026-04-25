package oas.work.lethalbreed.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import oas.work.lethalbreed.ai.HearingLogic;
import oas.work.lethalbreed.ai.TemporaryBlockTracker;
import oas.work.lethalbreed.ai.builder.ObstructionAnalyzer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
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
        ObstructionAnalyzer.onTick(((ServerLevel)(Object)this).getServer().getTickCount());
        TemporaryBlockTracker.onTick((ServerLevel)(Object)this);
    }

    @Inject(method = "gameEvent", at = @At("HEAD"))
    private void onEmit(net.minecraft.world.entity.Entity entity, GameEvent event, BlockPos pos, CallbackInfo ci) {
        
        for (GameEvent relevant : RELEVANT_EVENTS) {
            if (event == relevant) {
                HearingLogic.onGameEvent((ServerLevel)(Object)this, event, Vec3.atCenterOf(pos), entity);
                return;
            }
        }
    }
}







