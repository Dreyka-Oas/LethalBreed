package oas.work.lethalbreed.mixin;

import net.minecraft.server.MinecraftServer;
import oas.work.lethalbreed.ai.LethalThreads;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public class ServerShutdownMixin {

    @Inject(method = "stopServer", at = @At("TAIL"))
    private void onServerShutdown(CallbackInfo ci) {
        LethalThreads.shutdown();
    }
}
