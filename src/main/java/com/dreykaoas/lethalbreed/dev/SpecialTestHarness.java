package com.dreykaoas.lethalbreed.dev;

import com.dreykaoas.lethalbreed.config.domain.ProgressionConfig;

import com.dreykaoas.lethalbreed.LethalBreed;
import com.dreykaoas.lethalbreed.dev.special.SpecialTestArena;
import com.dreykaoas.lethalbreed.dev.special.SpecialTestCase;
import com.dreykaoas.lethalbreed.dev.special.SpecialTestEvaluator;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * Headless server-side verification of the special variants — no client needed. With
 * {@code devSpecialTest} on, builds a sheltered night arena near spawn, drops one forced special zombie per
 * type next to a stationary cow, lets the server AI run, then logs PASS/FAIL per ability. Turn the flag off
 * for shipping.
 */
public final class SpecialTestHarness {
    private SpecialTestHarness() {}

    private static int tick = -1;
    private static final List<SpecialTestCase> CASES = new ArrayList<>();

    public static void onTick(MinecraftServer server) {
        if (!ProgressionConfig.devSpecialTest) {
            return;
        }
        tick++;
        ServerLevel ow = server.overworld();
        if (tick == 5) {
            SpecialTestArena.build(ow, server, CASES);
        } else if (tick == 150) {
            SpecialTestEvaluator.killSplitters(ow, CASES);
        } else if (tick == 200) {
            SpecialTestEvaluator.evaluate(ow, CASES);
            LethalBreed.LOGGER.info("[SpecialTest] DONE");
        }
    }
}
