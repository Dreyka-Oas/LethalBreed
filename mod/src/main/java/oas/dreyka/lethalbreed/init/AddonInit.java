package oas.dreyka.lethalbreed.init;

import oas.dreyka.lethalbreed.LethalBreed;
import oas.dreyka.lethalbreed.api.LethalBreedAddon;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;

/** Calls every mod that declared a {@code lethalbreed:addon} entrypoint, before anything reads a registry. */
public final class AddonInit {
    private AddonInit() {}

    public static void register() {
        // getEntrypoints() builds every instance up front and rethrows the first failure, which would take the
        // whole load down; containers stay lazy so each instantiation can fail inside its own try.
        for (EntrypointContainer<LethalBreedAddon> container : FabricLoader.getInstance()
                .getEntrypointContainers("lethalbreed:addon", LethalBreedAddon.class)) {
            String modId = container.getProvider().getMetadata().getId();
            try {
                container.getEntrypoint().onLethalBreedReady();
            } catch (Throwable t) {
                // One broken addon costs its own wiring, never the whole load.
                LethalBreed.LOGGER.error("[LethalBreed] addon {} failed to initialise: {}", modId, t.toString());
            }
        }
    }
}
