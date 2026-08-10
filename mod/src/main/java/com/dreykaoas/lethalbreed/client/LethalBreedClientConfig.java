package com.dreykaoas.lethalbreed.client;

import com.dreykaoas.lethalbreed.LethalBreed;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Client-side optimization settings for LethalBreed, loaded from
 * {@code config/lethalbreed-client.json}. These control how the mod renders large zombie crowds and
 * how it cooperates with Sodium. Designed to be safe defaults that lean on Sodium when present.
 */
public final class LethalBreedClientConfig {

    /** Master switch for all mod client optimizations. */
    public boolean enabled = true;

    /** Skip rendering plain zombies farther than {@link #zombieRenderDistance} blocks. */
    public boolean cullDistantZombies = true;
    /** Distance (blocks) beyond which zombies are culled from rendering. */
    public double zombieRenderDistance = 96.0;

    /**
     * When Sodium is installed, defer to its frustum/chunk culling and keep the mod's render
     * tweaks conservative to avoid double work or conflicts.
     */
    public boolean adaptToSodium = true;

    // ---- runtime (not serialized) ----
    private static transient boolean sodiumPresent = false;
    private static transient boolean irisPresent = false;
    private static LethalBreedClientConfig instance = new LethalBreedClientConfig();

    public static LethalBreedClientConfig get() {
        return instance;
    }

    /** Effective per-frame distance cull, honoring the master + Sodium-adapt flags. */
    public double effectiveCullDistanceSq() {
        if (!enabled || !cullDistantZombies) {
            return Double.MAX_VALUE;
        }
        // With Sodium present and adapt enabled, stay generous (Sodium already culls aggressively).
        double d = (adaptToSodium && sodiumPresent) ? Math.max(zombieRenderDistance, 128.0) : zombieRenderDistance;
        return d * d;
    }

    public static void load() {
        sodiumPresent = FabricLoader.getInstance().isModLoaded("sodium");
        irisPresent = FabricLoader.getInstance().isModLoaded("iris");

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Path file = FabricLoader.getInstance().getConfigDir().resolve("lethalbreed-client.json");
        try {
            if (Files.exists(file)) {
                try (Reader r = Files.newBufferedReader(file)) {
                    LethalBreedClientConfig loaded = gson.fromJson(r, LethalBreedClientConfig.class);
                    if (loaded != null) {
                        instance = loaded;
                        instance.sanitize(); // Gson writes fields raw — guard the only value used at runtime
                    }
                }
            } else {
                Files.createDirectories(file.getParent());
                try (Writer w = Files.newBufferedWriter(file)) {
                    gson.toJson(instance, w);
                }
            }
        } catch (Exception e) {
            LethalBreed.LOGGER.warn("[LethalBreed] client config load failed, using defaults: {}", e.toString());
            instance = new LethalBreedClientConfig();
        }

        LethalBreed.LOGGER.info("[LethalBreed] client config — enabled={}, cull={}@{}b, sodium={}, iris={}",
                instance.enabled, instance.cullDistantZombies, instance.zombieRenderDistance,
                sodiumPresent, irisPresent);
    }

    /** Clamp the deserialized fields that are actually consumed at runtime. Only {@link #zombieRenderDistance}
     *  feeds {@link #effectiveCullDistanceSq}; a hand-edited {@code NaN}/negative there would break culling
     *  (NaN disables it, a negative is squared to a positive cull radius). */
    private void sanitize() {
        if (!Double.isFinite(zombieRenderDistance) || zombieRenderDistance < 0) {
            zombieRenderDistance = 96.0;
        }
    }
}
