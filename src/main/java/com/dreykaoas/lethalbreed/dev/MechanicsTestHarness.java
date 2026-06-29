package com.dreykaoas.lethalbreed.dev;

import com.dreykaoas.lethalbreed.config.domain.ProgressionConfig;

import com.dreykaoas.lethalbreed.LethalBreed;
import com.dreykaoas.lethalbreed.dev.mechanics.MechTestArena;
import com.dreykaoas.lethalbreed.dev.mechanics.MechTestEvaluator;
import com.dreykaoas.lethalbreed.dev.mechanics.MechTestState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Headless verification of the non-special mechanics: daylight burn (incl. Husk), phase-scaled gear/health,
 * and the Super Contamination plague (infect → death → zombify). Gated by {@code devMechTest}.
 */
public final class MechanicsTestHarness {
    private MechanicsTestHarness() {}

    private static int tick = -1;
    private static final MechTestState STATE = new MechTestState();

    public static void onTick(MinecraftServer server) {
        if (!ProgressionConfig.devMechTest) {
            return;
        }
        tick++;
        ServerLevel ow = server.overworld();
        if (tick == 5) {
            MechTestArena.build(ow, server, STATE);
        } else if (tick == 200) {
            MechTestEvaluator.evaluate(ow, STATE);
            LethalBreed.LOGGER.info("[MechTest] DONE");
        }
    }
}
