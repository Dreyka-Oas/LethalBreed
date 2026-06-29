package com.dreykaoas.lethalbreed.init;

import com.dreykaoas.lethalbreed.dev.DevSpawnScheduler;
import com.dreykaoas.lethalbreed.dev.MechanicsTestHarness;
import com.dreykaoas.lethalbreed.dev.SpecialTestHarness;
import com.dreykaoas.lethalbreed.tick.TickScheduler;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/** Registers the per-server-tick drivers in their original order. */
public final class TickInit {
    private TickInit() {}

    public static void register(TickScheduler scheduler) {
        // Drive the scheduler once per server tick.
        ServerTickEvents.END_SERVER_TICK.register(scheduler::onServerTick);
        // Dev: headless special-variant verification arena (no-op unless devSpecialTest).
        ServerTickEvents.END_SERVER_TICK.register(SpecialTestHarness::onTick);
        ServerTickEvents.END_SERVER_TICK.register(MechanicsTestHarness::onTick);
        // Dev/load-test spawn scheduler — drained each server tick (no-op unless /lethalspawn queued work).
        ServerTickEvents.END_SERVER_TICK.register(DevSpawnScheduler::tick);
    }
}
