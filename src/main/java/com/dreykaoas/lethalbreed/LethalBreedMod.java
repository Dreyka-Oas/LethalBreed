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
    }
}
