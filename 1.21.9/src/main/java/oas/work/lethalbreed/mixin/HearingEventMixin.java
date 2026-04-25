package oas.work.lethalbreed.mixin;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.event.GameEvent;
import net.minecraft.registry.entry.RegistryEntry;
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
        GameEvent.STEP.value(),
        GameEvent.BLOCK_PLACE.value(),
        GameEvent.BLOCK_DESTROY.value(),
        GameEvent.EAT.value(),
        GameEvent.HIT_GROUND.value(),
        GameEvent.PROJECTILE_LAND.value(),
        GameEvent.EXPLODE.value(),
        GameEvent.SPLASH.value(),
        GameEvent.ENTITY_PLACE.value(),
        GameEvent.TELEPORT.value()
    };

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        ObstructionAnalyzer.onTick(((ServerWorld)(Object)this).getServer().getTicks());
        TemporaryBlockTracker.onTick((ServerWorld)(Object)this);
    }

    @Inject(method = "emitGameEvent", at = @At("HEAD"))
    private void onEmit(RegistryEntry<GameEvent> event, Vec3d pos, GameEvent.Emitter emitter, CallbackInfo ci) {
        GameEvent eventValue = event.value();
        for (GameEvent relevant : RELEVANT_EVENTS) {
            if (eventValue == relevant) {
                HearingLogic.onGameEvent((ServerWorld)(Object)this, event, pos, emitter.sourceEntity());
                return;
            }
        }
    }
}





