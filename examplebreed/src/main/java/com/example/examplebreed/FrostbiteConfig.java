package com.example.examplebreed;

/** Options that land in config/oas/lethalbreed.json, under a Frostbite tab. Public static non-final
 *  primitives, exactly like LethalBreed's own holders. Every name starts with the claimed prefix. */
public final class FrostbiteConfig {
    private FrostbiteConfig() {}

    public static int frostbitePhase = 4;
    public static int frostbiteWeight = 12;
    public static double frostbiteRadius = 6.0;
    public static int frostbiteSlowSeconds = 8;
}
