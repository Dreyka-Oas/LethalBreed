package com.dreykaoas.lethalbreed.client;

import com.dreykaoas.lethalbreed.LethalBreed;
import com.dreykaoas.lethalbreed.client.gecko.HorrorRenderer;
import com.dreykaoas.lethalbreed.client.screen.CustomConfigScreen;
import com.dreykaoas.lethalbreed.entity.gecko.HorrorZombie;
import com.dreykaoas.lethalbreed.entity.gecko.LethalEntities;
import com.dreykaoas.lethalbreed.net.LethalConfigPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.resources.Identifier;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * Client entry point. Phase 1: just confirms the client side loads (alongside Sodium + Iris).
 * Bulk position packets, instanced rendering and the F3 debug overlay arrive in Phase 7.
 */
public final class LethalBreedClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        LethalBreedClientConfig.load();
        ContaminationScreenOverlay.register(); // faint green plague overlay while symptomatic
        // One GeckoLib renderer per horror-zombie variant — same renderer class, a per-variant model bound by id.
        for (LethalEntities.Variant v : LethalEntities.VARIANTS) {
            String id = v.id();
            EntityRendererRegistry.register(v.type(), ctx -> new HorrorRenderer<>(ctx,
                    new DefaultedEntityGeoModel<HorrorZombie>(
                            Identifier.fromNamespaceAndPath(LethalBreed.MOD_ID, id))));
        }
        // /lethalconfig → server sends the snapshot → open the config GUI on the client thread.
        ClientPlayNetworking.registerGlobalReceiver(LethalConfigPayloads.OpenConfig.TYPE, (payload, context) ->
                context.client().execute(() -> context.client().setScreen(
                        new CustomConfigScreen(payload.data()))));
        LethalBreed.LOGGER.info("[LethalBreed] client init — optimizations active (Sodium-aware).");
    }
}
