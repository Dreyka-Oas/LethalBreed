package oas.dreyka.lethalbreed.init;

import oas.dreyka.lethalbreed.config.io.ConfigIo;

import oas.dreyka.lethalbreed.config.domain.engine.SchedulerConfig;

import oas.dreyka.lethalbreed.config.LethalBreedConfig;
import oas.dreyka.lethalbreed.effect.ContaminationManager;
import oas.dreyka.lethalbreed.effect.LethalBreedEffects;
import oas.dreyka.lethalbreed.entity.ZombieStateAttachment;
import oas.dreyka.lethalbreed.net.LethalConfigPayloads;
import oas.dreyka.lethalbreed.pack.PackAttachment;
import oas.dreyka.lethalbreed.special.SpecialAttachment;
import oas.dreyka.lethalbreed.util.AiConflictDetector;

import static oas.dreyka.lethalbreed.LethalBreed.LOGGER;

/** One-shot startup bootstrap: config load, effect/attachment registration, mod-conflict detection. */
public final class BootstrapInit {
    private BootstrapInit() {}

    public static void run() {
        LethalBreedConfig.load();
        ConfigIo.load(); // JSON override: config/oas/lethalbreed.json
        // Right here and nowhere later: an addon deciding something off a config value has to see what the
        // player set, and every registration below is already free to read the loaded values itself.
        AddonInit.afterConfigLoad();
        LethalConfigPayloads.registerCommon(); // live-config networking + receiver
        LethalBreedEffects.register(); // custom effects FIRST because spawn/leap code references them
        oas.dreyka.lethalbreed.effect.LethalBreedPotions.register(); // brewable Super Contamination potion
        SpecialAttachment.init();
        ZombieStateAttachment.init(); // register the per-zombie behaviour-state attachment (drives sleep silence)
        ContaminationManager.init();
        PackAttachment.init(); // register the pack-membership attachment (survives chunk unload)
        AiConflictDetector.checkModList();
        LOGGER.info("[LethalBreed] init: MC 1.21.11, Java 21 (Liberica NIK/GraalVM). Buckets={}, cell={}b",
                SchedulerConfig.tickBuckets, SchedulerConfig.spatialCellSize);
    }
}
