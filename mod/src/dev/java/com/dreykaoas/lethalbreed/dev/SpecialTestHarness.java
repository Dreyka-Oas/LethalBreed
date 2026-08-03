package com.dreykaoas.lethalbreed.dev;

import com.dreykaoas.lethalbreed.config.domain.engine.DevTestConfig;

import com.dreykaoas.lethalbreed.LethalBreed;
import com.dreykaoas.lethalbreed.dev.special.SpecialTestArena;
import com.dreykaoas.lethalbreed.dev.special.SpecialTestCase;
import com.dreykaoas.lethalbreed.dev.special.SpecialTestEvaluator;
import net.fabricmc.loader.api.FabricLoader;
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
        // Dev-env gate: this builds a block arena and force-spawns mobs near spawn. Even if a user toggles
        // devSpecialTest in the GUI, it must NEVER run on a shipped jar / real world — only under gradle
        // runServer (a development environment), where headless verification is intended.
        if (!DevTestConfig.devSpecialTest || !FabricLoader.getInstance().isDevelopmentEnvironment()) {
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
            // DevVerdict.summary is what emits the load-bearing ALL DONE marker the gate reads. The old
            // "[SpecialTest] DONE" looked like the same thing and was invisible to every tool.
            DevVerdict.summary(SpecialTestEvaluator.SUITE, server);
        }
    }
}
