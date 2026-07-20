package com.dreykaoas.lethalbreed.client;

import com.dreykaoas.lethalbreed.LethalBreed;
import com.dreykaoas.lethalbreed.client.gecko.HorrorZombieRenderer;
import com.dreykaoas.lethalbreed.client.screen.CustomConfigScreen;
import com.dreykaoas.lethalbreed.net.LethalConfigPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.world.entity.EntityType;

/**
 * Client entry point. Phase 1: just confirms the client side loads (alongside Sodium + Iris).
 * Bulk position packets, instanced rendering and the F3 debug overlay arrive in Phase 7.
 */
public final class LethalBreedClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        LethalBreedClientConfig.load();
        ContaminationScreenOverlay.register(); // faint green plague overlay while symptomatic
        // Replace the vanilla zombie renderer with our GeckoLib one: every zombie flows through it, and each
        // renders its per-instance model (0 = plain look, 1..15 = a horror model) chosen from its attachment.
        EntityRendererRegistry.register(EntityType.ZOMBIE, HorrorZombieRenderer::new);
        // /lethalconfig → server sends the snapshot → open the config GUI on the client thread.
        ClientPlayNetworking.registerGlobalReceiver(LethalConfigPayloads.OpenConfig.TYPE, (payload, context) ->
                context.client().execute(() -> context.client().setScreen(
                        new CustomConfigScreen(payload.data()))));
        LethalBreed.LOGGER.info("[LethalBreed] client init — optimizations active (Sodium-aware).");
    }
}
