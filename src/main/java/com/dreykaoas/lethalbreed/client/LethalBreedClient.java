package com.dreykaoas.lethalbreed.client;

import com.dreykaoas.lethalbreed.LethalBreed;
import com.dreykaoas.lethalbreed.client.screen.CustomConfigScreen;
import com.dreykaoas.lethalbreed.net.LethalConfigPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Client entry point. Phase 1: just confirms the client side loads (alongside Sodium + Iris).
 * Bulk position packets, instanced rendering and the F3 debug overlay arrive in Phase 7.
 */
public final class LethalBreedClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        LethalBreedClientConfig.load();
        // /lethalconfig → server sends the snapshot → open the config GUI on the client thread.
        ClientPlayNetworking.registerGlobalReceiver(LethalConfigPayloads.OpenConfig.TYPE, (payload, context) ->
                context.client().execute(() -> context.client().setScreen(
                        new CustomConfigScreen(payload.data()))));
        LethalBreed.LOGGER.info("[LethalBreed] client init — optimizations active (Sodium-aware).");
    }
}
