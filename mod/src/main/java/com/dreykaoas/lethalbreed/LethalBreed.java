package com.dreykaoas.lethalbreed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dependency-free constants and shared logger for the mod. Lives apart from {@link LethalBreedMod}
 * (the {@code ModInitializer} composition root) so the many classes that only need the logger or
 * the mod id depend on this leaf instead of the entry point — keeping the entry point free of
 * inbound dependencies.
 */
public final class LethalBreed {
    private LethalBreed() {}

    public static final String MOD_ID = "lethalbreed";
    public static final Logger LOGGER = LoggerFactory.getLogger("LethalBreed");
}
