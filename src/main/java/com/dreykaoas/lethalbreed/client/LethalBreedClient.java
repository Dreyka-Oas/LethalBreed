package com.dreykaoas.lethalbreed.client;

import com.dreykaoas.lethalbreed.LethalBreedMod;
import net.fabricmc.api.ClientModInitializer;

/**
 * Client entry point. Phase 1: just confirms the client side loads (alongside Sodium + Iris).
 * Bulk position packets, instanced rendering and the F3 debug overlay arrive in Phase 7.
 */
public final class LethalBreedClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        LethalBreedClientConfig.load();
        LethalBreedMod.LOGGER.info("[LethalBreed] client init — optimizations active (Sodium-aware).");
    }
}
