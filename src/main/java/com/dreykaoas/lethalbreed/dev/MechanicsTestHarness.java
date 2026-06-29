package com.dreykaoas.lethalbreed.dev;

import com.dreykaoas.lethalbreed.config.domain.ProgressionConfig;

import com.dreykaoas.lethalbreed.LethalBreed;
import com.dreykaoas.lethalbreed.dev.mechanics.MechTestArena;
import com.dreykaoas.lethalbreed.dev.mechanics.MechTestEvaluator;
import com.dreykaoas.lethalbreed.dev.mechanics.MechTestState;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Headless verification of the non-special mechanics: daylight burn (incl. Husk), phase-scaled gear/health,
 * and the Super Contamination plague (infect → death → zombify). Gated by {@code devMechTest}.
 */
public final class MechanicsTestHarness {
    private MechanicsTestHarness() {}

    private static int tick = -1;
    /** Evaluate after a generous window so sun-burn reliably ignites in the playerless headless arena. */
    private static final int EVAL_TICK = 400;
    private static final MechTestState STATE = new MechTestState();

    public static void onTick(MinecraftServer server) {
        // Dev-env gate: builds an arena + force-spawns mobs. Never run on a shipped jar / real world even if
        // the GUI toggle is on — only under gradle runServer (a development environment).
        if (!ProgressionConfig.devMechTest || !FabricLoader.getInstance().isDevelopmentEnvironment()) {
            return;
        }
        tick++;
        ServerLevel ow = server.overworld();
        if (tick == 5) {
            MechTestArena.build(ow, server, STATE);
        } else if (tick > 5 && tick < EVAL_TICK) {
            // Latch the fire state every tick: a husk/zombie can ignite in daylight and burn to death before
            // the eval tick, so the evaluator must know it WAS on fire, not just whether it still is right now.
            // The window is generous (EVAL_TICK) because sun-burn only fires on a LOD bucket activation and a
            // freshly force-loaded headless arena needs a few ticks for skylight to settle before it ignites.
            STATE.latchFire();
        } else if (tick == EVAL_TICK) {
            MechTestEvaluator.evaluate(ow, STATE);
            LethalBreed.LOGGER.info("[MechTest] DONE");
        }
    }
}
