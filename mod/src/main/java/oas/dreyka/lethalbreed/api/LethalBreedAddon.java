package oas.dreyka.lethalbreed.api;

/**
 * What another mod implements to be called by LethalBreed. Declare the class under the
 * {@code lethalbreed:addon} entrypoint of your own {@code fabric.mod.json}:
 *
 * <pre>
 * "entrypoints": { "lethalbreed:addon": ["com.example.mymod.MyAddon"] }
 * </pre>
 *
 * <p>Called once, after the config is loaded and before anything reads a registry, so a listener registered
 * here never misses an event and an allowed AI namespace is known before the first zombie loads.
 */
@FunctionalInterface
public interface LethalBreedAddon {
    void onLethalBreedReady();
}
