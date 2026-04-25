package oas.work.lethalbreed.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.Entity;
import oas.work.lethalbreed.ai.HearingLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public class HearingEventMixin {

    @Inject(method = "gameEvent", at = @At("HEAD"))
    private void onGameEvent(GameEvent event, Vec3 pos, GameEvent.Context context, CallbackInfo ci) {
        HearingLogic.onGameEvent((ServerLevel) (Object) this, event, pos, context.sourceEntity());
    }
}
