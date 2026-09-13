package oas.dreyka.lethalbreed.init;

import oas.dreyka.lethalbreed.LethalBreed;
import oas.dreyka.lethalbreed.api.LethalBreedAddon;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Calls every mod that declared a {@code lethalbreed:addon} entrypoint, in two passes.
 *
 * <p>{@link #register} runs before the config file is read, so an addon's options are in the schema by the
 * time the loader matches names against it. {@link #afterConfigLoad} runs straight after that read. The
 * order matters in that direction only: an addon that declares late loses the player's saved values, while
 * an addon that reads early merely reads a default.
 *
 * <p>Each call is isolated, so one broken addon costs its own wiring and not the load. What isolation does
 * not undo is a half-finished registration: an addon that claims a config prefix and then throws leaves the
 * claim standing, and its options will not be there to go with it.
 */
public final class AddonInit {
    private AddonInit() {}

    // Resolved once by register(), so the second pass reaches the same objects rather than asking the loader
    // again and hoping it hands back the instance it built rather than a fresh one. An addon whose class
    // failed to load never lands here and is simply not called again.
    private static final List<Map.Entry<String, LethalBreedAddon>> ADDONS = new ArrayList<>();

    public static void register() {
        // getEntrypoints() builds every instance up front and rethrows the first failure, which would take the
        // whole load down; containers stay lazy so each instantiation can fail inside its own try.
        for (EntrypointContainer<LethalBreedAddon> container : FabricLoader.getInstance()
                .getEntrypointContainers("lethalbreed:addon", LethalBreedAddon.class)) {
            String modId = container.getProvider().getMetadata().getId();
            try {
                ADDONS.add(Map.entry(modId, container.getEntrypoint()));
            } catch (Throwable t) {
                LethalBreed.LOGGER.error("[LethalBreed] addon {} failed to load: {}", modId, t.toString());
            }
        }
        each("initialise", LethalBreedAddon::onLethalBreedReady);
    }

    /** Second pass, from {@link BootstrapInit} once {@code lethalbreed.json} has been read and clamped. */
    public static void afterConfigLoad() {
        each("read the config", LethalBreedAddon::onConfigLoaded);
    }

    private static void each(String what, Consumer<LethalBreedAddon> call) {
        for (Map.Entry<String, LethalBreedAddon> addon : ADDONS) {
            try {
                call.accept(addon.getValue());
            } catch (Throwable t) {
                LethalBreed.LOGGER.error("[LethalBreed] addon {} failed to {}: {}",
                        addon.getKey(), what, t.toString());
            }
        }
    }
}
