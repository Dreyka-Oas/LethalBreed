package com.dreykaoas.lethalbreed.init;

import com.dreykaoas.lethalbreed.tick.TickScheduler;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/**
 * Registers the per-server-tick drivers. Dev-only tick listeners (test harnesses, load-test spawn queue)
 * live in the {@code dev} source set and are wired by {@code DevBootstrap}, not here.
 */
public final class TickInit {
    private TickInit() {}

    public static void register(TickScheduler scheduler) {
        // Drive the scheduler once per server tick.
        ServerTickEvents.END_SERVER_TICK.register(scheduler::onServerTick);
    }
}
