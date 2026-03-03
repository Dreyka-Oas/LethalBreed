/**
 * Project: Lethal Breed
 * Responsibility: Mixin to Hook into Game Events for Hearing
 * License: O.A.S - MS-RSL (Microsoft Reference Source License)
 * Copyright (c) 2026 O.A.S (Optimization & Quality). All rights reserved.
 */
package oas.work.lethalbreed.mixin;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.event.GameEvent;
import net.minecraft.registry.entry.RegistryEntry;
import oas.work.lethalbreed.ai.HearingLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerWorld.class)
public abstract class HearingEventMixin {
    @Inject(method = "emitGameEvent", at = @At("HEAD"))
    private void onEmit(RegistryEntry<GameEvent> event, Vec3d pos, GameEvent.Emitter emitter, CallbackInfo ci) {
        HearingLogic.onGameEvent((ServerWorld)(Object)this, event, pos, emitter.sourceEntity());
    }
}