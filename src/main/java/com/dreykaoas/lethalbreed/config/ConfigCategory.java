package com.dreykaoas.lethalbreed.config;

import java.util.Locale;

/**
 * Maps an option name to its GUI tab category. First match wins; ordered specific→generic.
 */
public final class ConfigCategory {
    private ConfigCategory() {}

    public static String of(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        // dev/debug-named options belong in the Dev tab regardless of other keyword matches (e.g.
        // devClimbTest/debugClimb would otherwise be pulled into Climb by the "climb" rule below).
        if (n.startsWith("dev") || n.startsWith("debug")) return "Dev";
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
        // Compute MUST precede the flow/nav rule so flowCpuThreads + gpu* route here, not to Pathing.
        if (n.equals("usegpu") || n.equals("flowcputhreads") || n.contains("gpu")) return "Compute";
        // navReissueInterval lives with its lod*NavMultiplier siblings on the Perf tab, not Pathing.
        if (n.equals("navreissueinterval")) return "Perf";
        // flow/nav MUST precede the break rule so flowBreakCost (a path cost) groups with its sibling
        // flow*Cost knobs under Pathing instead of being captured by "break" → Breaking.
        if (n.startsWith("flow") || n.startsWith("nav")) return "Pathing";
        if (n.contains("break") || n.contains("blockops") || n.contains("placedblock")) return "Breaking";
        if (n.contains("target") || n.contains("nearest") || n.contains("lineofsight")
                || n.contains("attack")) return "Targeting";
        if (n.contains("lod") || n.contains("throttle") || n.contains("bucket")
                || n.contains("spatial") || n.contains("reclassify") || n.contains("budget")
                || n.contains("mspt")) return "Perf";
        if (n.contains("day") || n.contains("weather") || n.contains("suppressvanilla")
                || n.contains("failonai")) return "World";
        if (n.contains("baby") || n.contains("drowned") || n.contains("strip")
                || n.contains("sun")) return "Spawn";
        return "Misc";
    }
}
