package oas.work.lethalbreed.mixin;

import net.minecraft.core.Holder;
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
        GameEvent.STEP.value(),
        GameEvent.BLOCK_PLACE.value(),
        GameEvent.BLOCK_DESTROY.value(),
        GameEvent.EAT.value(),
        GameEvent.HIT_GROUND.value(),
        GameEvent.PROJECTILE_LAND.value(),
        GameEvent.EXPLODE.value(),
        GameEvent.SPLASH.value(),
        GameEvent.ENTITY_PLACE.value()
    };

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        ObstructionAnalyzer.onTick(((ServerLevel)(Object)this).getServer().getTickCount());
        TemporaryBlockTracker.onTick((ServerLevel)(Object)this);
    }

    @Inject(method = "gameEvent", at = @At("HEAD"))
    private void onEmit(Holder<GameEvent> event, Vec3 pos, GameEvent.Context emitter, CallbackInfo ci) {
        GameEvent eventValue = event.value();
        
        for (GameEvent relevant : RELEVANT_EVENTS) {
            if (eventValue == relevant) {
                HearingLogic.onGameEvent((ServerLevel)(Object)this, event, pos, emitter.sourceEntity());
                return;
            }
        }
    }
}









