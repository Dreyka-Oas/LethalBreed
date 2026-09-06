package oas.dreyka.lethalbreed.api;

/**
 * What another mod implements to be called by LethalBreed. Declare the class under the
 * {@code lethalbreed:addon} entrypoint of your own {@code fabric.mod.json}:
 *
 * <pre>
 * "entrypoints": { "lethalbreed:addon": ["com.example.mymod.MyAddon"] }
 * </pre>
 *
 * <p>You are called twice, and which of the two you want depends on whether you are declaring something or
 * reading something.
 */
@FunctionalInterface
public interface LethalBreedAddon {

    /**
     * Declare, before the config file is read and before anything reads a registry.
     *
     * <p>This is where {@link LethalBreedConfigApi#register} goes: options that join the schema after the
     * read are options the loader cannot match, so a player's saved values for them are warned about and
     * dropped on the next write. Allowed AI namespaces and phase listeners belong here too, early enough to
     * be known before the first zombie loads.
     *
     * <p>Nothing you read here is a config value yet. The fields still hold the defaults their holder
     * declared, not what the player set.
     */
    void onLethalBreedReady();

    /**
     * Act on what the player actually configured, once the file has been read and clamped.
     *
     * <p>Empty by default, because most addons only declare. Override it when a decision of yours depends on
     * an option value, yours or this mod's: deciding it in {@link #onLethalBreedReady()} would read the
     * default and then never hear that the real value was different.
     */
    default void onConfigLoaded() {}
}
