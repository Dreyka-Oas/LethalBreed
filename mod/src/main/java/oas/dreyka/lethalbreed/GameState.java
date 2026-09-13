package oas.dreyka.lethalbreed;

import oas.dreyka.lethalbreed.dimension.DimensionManager;
import oas.dreyka.lethalbreed.entity.ZombieRegistry;

/**
 * Process-wide runtime state, created once and wired by {@link LethalBreedMod} at init. Lives apart
 * from the entry point so commands and dev tools read shared state from this holder instead of
 * depending on the {@code ModInitializer}, keeping the entry point free of inbound dependencies.
 */
public final class GameState {
    private GameState() {}

    public static final ZombieRegistry REGISTRY = new ZombieRegistry();
    public static final DimensionManager DIMENSIONS = new DimensionManager();
}
