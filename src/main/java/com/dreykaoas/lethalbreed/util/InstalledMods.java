package com.dreykaoas.lethalbreed.util;

import com.dreykaoas.lethalbreed.LethalBreedMod;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Detects which optimization companion mods are present, so LethalBreed can log the environment and
 * adapt behavior (e.g. lean on Krypton/VMP for entity-tracking networking instead of custom packets).
 */
public final class InstalledMods {
    private InstalledMods() {}

    public static boolean krypton;
    public static boolean lithium;
    public static boolean vmp;
    public static boolean c2me;
    public static boolean ferritecore;
    public static boolean servercore;
    public static boolean scalablelux;
    public static boolean immersiveOptimization;
    public static boolean spark;
    public static boolean sodium;
    public static boolean iris;

    public static void detect() {
        FabricLoader fl = FabricLoader.getInstance();
        krypton = fl.isModLoaded("krypton");
        lithium = fl.isModLoaded("lithium");
        vmp = fl.isModLoaded("vmp") || fl.isModLoaded("vmp-fabric");
        c2me = fl.isModLoaded("c2me");
        ferritecore = fl.isModLoaded("ferritecore");
        servercore = fl.isModLoaded("servercore");
        scalablelux = fl.isModLoaded("scalablelux");
        immersiveOptimization = fl.isModLoaded("immersive_optimization") || fl.isModLoaded("immersive-optimization");
        spark = fl.isModLoaded("spark");
        sodium = fl.isModLoaded("sodium");
        iris = fl.isModLoaded("iris");

        LethalBreedMod.LOGGER.info(
                "[LethalBreed] perf mods: krypton={} lithium={} vmp={} c2me={} ferritecore={} servercore={} scalablelux={} immersiveOpt={} spark={}",
                krypton, lithium, vmp, c2me, ferritecore, servercore, scalablelux, immersiveOptimization, spark);
        if (krypton || vmp) {
            LethalBreedMod.LOGGER.info("[LethalBreed] network handled by Krypton/VMP — relying on optimized vanilla entity tracking (no custom bulk packets).");
        }
    }
}
