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
        ContaminationScreenOverlay.register(); // faint green plague overlay while symptomatic
        // /lethalconfig → server sends the snapshot → open the config GUI on the client thread.
        // Only open over NO screen (or refresh our own): a server that spams OpenConfig otherwise rips away
        // whatever screen the player is on — pause menu, a container, the chat box (audit #24). The legitimate
        // path is unaffected: submitting /lethalconfig closes the chat screen before the round-trip, so the
        // client is on no screen when the packet lands.
        ClientPlayNetworking.registerGlobalReceiver(LethalConfigPayloads.OpenConfig.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    var mc = context.client();
                    if (mc.screen != null && !(mc.screen instanceof CustomConfigScreen)) {
                        return;
                    }
                    mc.setScreen(new CustomConfigScreen(payload.data()));
                }));
        LethalBreed.LOGGER.info("[LethalBreed] client init — optimizations active (Sodium-aware).");
    }
}
