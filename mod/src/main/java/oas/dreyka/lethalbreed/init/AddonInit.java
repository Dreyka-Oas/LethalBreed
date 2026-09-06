package oas.dreyka.lethalbreed.init;

import oas.dreyka.lethalbreed.LethalBreed;
import oas.dreyka.lethalbreed.api.LethalBreedAddon;

import net.fabricmc.loader.api.FabricLoader;

/** Calls every mod that declared a {@code lethalbreed:addon} entrypoint, before anything reads a registry. */
public final class AddonInit {
    private AddonInit() {}

    public static void register() {
        for (LethalBreedAddon addon : FabricLoader.getInstance()
                .getEntrypoints("lethalbreed:addon", LethalBreedAddon.class)) {
            try {
                addon.onLethalBreedReady();
            } catch (Throwable t) {
                // One broken addon costs its own wiring, never the whole load.
                LethalBreed.LOGGER.error("[LethalBreed] addon {} failed to initialise: {}",
                        addon.getClass().getName(), t.toString());
            }
        }
    }
}
