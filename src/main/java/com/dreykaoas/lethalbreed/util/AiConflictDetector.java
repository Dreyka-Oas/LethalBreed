package com.dreykaoas.lethalbreed.util;

import com.dreykaoas.lethalbreed.LethalBreedMod;
import com.dreykaoas.lethalbreed.config.LethalBreedConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Mob;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Detects mods that change zombie AI <b>behaviour</b> — which conflicts with LethalBreed (we already
 * drive zombies). Two layers:
 *
 * <ol>
 *   <li><b>Known list</b> — a curated set of mod IDs checked at startup (perf mods like Lithium that
 *       keep behaviour identical are NOT here).</li>
 *   <li><b>Behavioural auto-detect</b> — scans a real zombie's goals once; any goal class that is
 *       neither vanilla ({@code net.minecraft.*}) nor ours means another mod injected zombie AI.
 *       This catches <i>any</i> such mod without knowing its id.</li>
 * </ol>
 *
 * On conflict: loud log, and if {@link LethalBreedConfig#failOnAiConflict} (default true) a hard stop
 * — i.e. the mods are treated as incompatible.
 */
public final class AiConflictDetector {
    private AiConflictDetector() {}

    /** Curated mod ids that alter mob/zombie AI behaviour (extend as needed). Mirror in fabric.mod.json "breaks". */
    private static final Set<String> KNOWN_AI_MODS = Set.of(
            "enhancedai", "enhanced_ai",
            "special_ai", "specialai",
            "ai_improvements", "aiimprovements",
            "zombie_awareness", "zombieawareness",
            "mobsplus", "savage_and_ravage_ai",
            "smarter_mobs", "smartermobs",
            "betterzombies", "better_zombies",
            "monster_ai", "monsterai"
    );

    private static boolean scanned = false;

    /** Startup check against the known-id list. */
    public static void checkModList() {
        FabricLoader fl = FabricLoader.getInstance();
        List<String> present = KNOWN_AI_MODS.stream().filter(fl::isModLoaded).distinct().toList();
        if (!present.isEmpty()) {
            report("known zombie-AI mods present: " + present);
        }
    }

    /** Behavioural scan on a real zombie (runs once). Uses removeAllGoals with a no-op predicate to
     *  iterate every registered goal without removing any. */
    public static void scanZombie(Mob zombie) {
        if (scanned) {
            return;
        }
        scanned = true;
        Set<String> foreign = new LinkedHashSet<>();
        zombie.removeAllGoals(goal -> {
            String cls = goal.getClass().getName();
            if (!cls.startsWith("net.minecraft.") && !cls.startsWith("com.dreykaoas.lethalbreed")) {
                foreign.add(cls);
            }
            return false; // scan only — never remove
        });
        if (foreign.isEmpty()) {
            LethalBreedMod.LOGGER.info("[LethalBreed] AI-conflict scan: clean (no foreign zombie goals).");
        } else {
            report("foreign zombie AI goals injected by another mod: " + foreign);
        }
    }

    private static void report(String detail) {
        LethalBreedMod.LOGGER.error("[LethalBreed] AI CONFLICT — {}", detail);
        if (LethalBreedConfig.failOnAiConflict) {
            throw new IllegalStateException(
                    "LethalBreed is incompatible with mods that modify zombie AI (" + detail + "). "
                    + "Remove the conflicting mod, or set failOnAiConflict=false in config/lethalbreed.json.");
        }
        LethalBreedMod.LOGGER.warn("[LethalBreed] continuing despite conflict (failOnAiConflict=false) — zombie behaviour may be unpredictable.");
    }
}
