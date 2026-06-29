package com.dreykaoas.lethalbreed.config;

/**
 * Lightweight runtime config entry point. The option schema lives in the domain holder classes under
 * {@code config.domain} (enumerated by {@link ConfigSchema#all()}); this class just exposes the
 * {@link #load()} hook callers invoke on server start.
 *
 * <p>Defaults live on the holders; a JSON loader ({@code config/oas/lethalbreed.json}) can override them.
 */
public final class LethalBreedConfig {
    private LethalBreedConfig() {}

    public static void load() {
        // Phase 1: defaults only. JSON override hook added in a later phase.
    }
}
