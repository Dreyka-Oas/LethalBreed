/**
 * Project: Lethal Breed
 * Responsibility: Main Mod Entry Point
 * License: O.A.S - MS-RSL (Microsoft Reference Source License)
 * Copyright (c) 2026 O.A.S (Optimization & Quality). All rights reserved.
 */
package oas.work.lethalbreed;
import oas.work.lethalbreed.config.ModConfig;

import net.fabricmc.api.ModInitializer;

public class LethalBreed implements ModInitializer {
    @Override
    public void onInitialize() {
        ModConfig.load();
    }
}