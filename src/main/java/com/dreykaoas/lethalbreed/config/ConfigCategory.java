package com.dreykaoas.lethalbreed.config;

import java.util.Locale;

/**
 * Maps an option name to its GUI tab category. First match wins; ordered specific→generic.
 */
public final class ConfigCategory {
    private ConfigCategory() {}

    public static String of(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.contains("contam")) return "Contamination";
        if (n.startsWith("special")) return "Specials";
        if (n.startsWith("phase")) return "Phases";
        if (n.contains("sound")) return "Sound";
        if (n.contains("water") || n.contains("float")) return "Water";
        if (n.contains("randomeffect") || n.contains("leapeffect")) return "Effects";
        if (n.startsWith("var") || n.equals("enablevariation")) return "Variation";
        if (n.startsWith("leap")) return "Leap";
        if (n.contains("climb") || n.contains("pillar") || n.contains("stuck")
                || n.contains("descend") || n.contains("safedrop") || n.contains("jump")
                || n.contains("melee") || n.contains("fall")) return "Climb";
        if (n.contains("break") || n.contains("blockops") || n.contains("placedblock")) return "Breaking";
        if (n.equals("usegpu") || n.equals("flowcputhreads")) return "Compute";
        if (n.startsWith("flow") || n.startsWith("nav")) return "Pathing";
        if (n.contains("target") || n.contains("nearest") || n.contains("lineofsight")
                || n.contains("attack")) return "Targeting";
        if (n.contains("lod") || n.contains("throttle") || n.equals("tickbuckets")
                || n.contains("spatial") || n.contains("reclassify")) return "Perf";
        if (n.startsWith("dev") || n.startsWith("debug")) return "Dev";
        if (n.contains("day") || n.contains("weather") || n.contains("suppressvanilla")
                || n.contains("failonai")) return "World";
        if (n.contains("baby") || n.contains("drowned") || n.contains("strip")
                || n.contains("sun")) return "Spawn";
        return "Misc";
    }
}
