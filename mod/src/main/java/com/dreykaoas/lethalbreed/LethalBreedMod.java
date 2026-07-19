package com.dreykaoas.lethalbreed;

import com.dreykaoas.lethalbreed.dimension.DimensionManager;
import com.dreykaoas.lethalbreed.entity.ZombieRegistry;
import com.dreykaoas.lethalbreed.init.BootstrapInit;
import com.dreykaoas.lethalbreed.init.CommandInit;
import com.dreykaoas.lethalbreed.init.EntityEventsInit;
import com.dreykaoas.lethalbreed.init.LifecycleInit;
import com.dreykaoas.lethalbreed.init.TickInit;
import com.dreykaoas.lethalbreed.tick.TickScheduler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Entry point for LethalBreed.
 *
 * <p>Phase 1 scope (current): bootstrap the runtime spine — register vanilla zombies into a
 * {@link ZombieRegistry}, drive them through a staggered {@link TickScheduler}, and maintain a
 * per-dimension {@link DimensionManager} (spatial grid now, flow field later). All work runs on
 * the server thread for now; off-thread compute (flow field, GPU) arrives in later phases behind
 * the thread-safety discipline described in plan.md.
 *
 * <p>Registration is split into {@code init.*} helpers; {@code onInitialize} keeps the original order.
 */
public final class LethalBreedMod implements ModInitializer {
    private static final DimensionManager DIMENSIONS = GameState.DIMENSIONS;
    private static final ZombieRegistry REGISTRY = GameState.REGISTRY;
    private static final TickScheduler SCHEDULER = new TickScheduler(REGISTRY, DIMENSIONS);

    @Override
    public void onInitialize() {
        BootstrapInit.run();
        EntityEventsInit.register(REGISTRY, DIMENSIONS);
        TickInit.register(SCHEDULER);
        CommandInit.register();
        LifecycleInit.register(REGISTRY, DIMENSIONS);
        installDevHooks();
    }

    /**
     * Wire the development-only harnesses and load-test command — but ONLY in a development environment.
     * The dev code lives in a separate {@code dev} source set that is never packaged into the shipped jar,
     * so we load its entry point ({@code com.dreykaoas.lethalbreed.dev.DevBootstrap}) reflectively: on a
     * production jar the class is absent and the lookup fails silently, leaving zero dev wiring active.
     */
    private static void installDevHooks() {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment()) {
            return;
        }
        try {
            Class.forName("com.dreykaoas.lethalbreed.dev.DevBootstrap")
                    .getMethod("install")
                    .invoke(null);
        } catch (ClassNotFoundException e) {
            // Dev source set not on the classpath (shipped jar) — expected, nothing to install.
            LethalBreed.LOGGER.debug("[LethalBreed] no dev source set on classpath; skipping dev hooks.");
        } catch (ReflectiveOperationException e) {
            LethalBreed.LOGGER.warn("[LethalBreed] failed to install dev hooks", e);
        }
    }
}
