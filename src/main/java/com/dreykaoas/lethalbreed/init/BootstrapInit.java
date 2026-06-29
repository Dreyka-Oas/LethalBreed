package com.dreykaoas.lethalbreed.init;

import com.dreykaoas.lethalbreed.config.domain.SchedulerConfig;

import com.dreykaoas.lethalbreed.config.ConfigIo;
import com.dreykaoas.lethalbreed.config.LethalBreedConfig;
import com.dreykaoas.lethalbreed.effect.ContaminationManager;
import com.dreykaoas.lethalbreed.effect.LethalBreedEffects;
import com.dreykaoas.lethalbreed.net.LethalConfigPayloads;
import com.dreykaoas.lethalbreed.special.SpecialAttachment;
import com.dreykaoas.lethalbreed.util.AiConflictDetector;
import com.dreykaoas.lethalbreed.util.InstalledMods;

import static com.dreykaoas.lethalbreed.LethalBreed.LOGGER;

/** One-shot startup bootstrap: config load, effect/attachment registration, mod-conflict detection. */
public final class BootstrapInit {
    private BootstrapInit() {}

    public static void run() {
        LethalBreedConfig.load();
        ConfigIo.load(); // JSON override: config/oas/lethalbreed.json
        LethalConfigPayloads.registerCommon(); // live-config networking + receiver
        LethalBreedEffects.register(); // custom effects FIRST — spawn/leap code references them
        SpecialAttachment.init(); // register the special-type attachment
        ContaminationManager.init(); // register the contamination attachment
        InstalledMods.detect();
        AiConflictDetector.checkModList();
        LOGGER.info("[LethalBreed] init — MC 1.21.11, Java 21 (Liberica NIK/GraalVM). Buckets={}, cell={}b",
                SchedulerConfig.tickBuckets, SchedulerConfig.spatialCellSize);
    }
}
