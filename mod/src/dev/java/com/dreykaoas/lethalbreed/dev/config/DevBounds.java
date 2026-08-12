package com.dreykaoas.lethalbreed.dev.config;

import com.dreykaoas.lethalbreed.config.BoundsRegistrar;

/**
 * Clamp ranges for the dev-only options in {@link DevTestConfig}. Registered at runtime by
 * {@code DevBootstrap} through {@code ConfigBounds.registerGroup}, so {@code ConfigBoundsTable}'s static
 * block never names it and a shipped jar has no trace of it.
 *
 * <p>A field and its bound always move together — {@code ConfigBoundsTest} fails the build otherwise. These
 * two ranges are the ones {@code ProgressionBounds} and {@code PerfBounds} used to hold, unchanged.
 */
public final class DevBounds {
    private DevBounds() {}

    public static void register(BoundsRegistrar r) {
        r.b("devSpawnRadius", 1, 256);
        r.b("debugLogInterval", 0, 1_000_000);
    }
}
