package com.dreykaoas.lethalbreed.util;

import com.dreykaoas.lethalbreed.config.domain.TargetingConfig;

import com.dreykaoas.lethalbreed.LethalBreed;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
 * On conflict: loud log, and if {@link com.dreykaoas.lethalbreed.config.domain.TargetingConfig#failOnAiConflict} (default true) a hard stop
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

    /** Startup check against the known-id list. Runs from {@code BootstrapInit}, where throwing is correct —
     *  the server has not started yet, so a hard stop is a clean "won't launch". */
    public static void checkModList() {
        FabricLoader fl = FabricLoader.getInstance();
        List<String> present = KNOWN_AI_MODS.stream().filter(fl::isModLoaded).distinct().toList();
        if (!present.isEmpty()) {
            reportAtBoot("known zombie-AI mods present: " + present);
        }
    }

    /** Behavioural scan on a real zombie (runs once). Uses removeAllGoals with a no-op predicate to
     *  iterate every registered goal without removing any. Called from the {@code ENTITY_LOAD} callback —
     *  i.e. mid-tick, in a running session — so a conflict here must NOT throw (see {@link #reportInSession}).
     *  The {@code level} is threaded through so the session handler can stop the server cleanly. */
    public static void scanZombie(Mob zombie, ServerLevel level) {
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
            LethalBreed.LOGGER.info("[LethalBreed] AI-conflict scan: clean (no foreign zombie goals).");
        } else {
            reportInSession("foreign zombie AI goals injected by another mod: " + foreign, level);
        }
    }

    /** Boot-time policy: hard {@code throw}. Only reachable before the server is running. */
    private static void reportAtBoot(String detail) {
        LethalBreed.LOGGER.error("[LethalBreed] AI CONFLICT — {}", detail);
        if (TargetingConfig.failOnAiConflict) {
            throw new IllegalStateException(
                    "LethalBreed is incompatible with mods that modify zombie AI (" + detail + "). "
                    + "Remove the conflicting mod, or set failOnAiConflict=false in config/oas/lethalbreed.json.");
        }
        LethalBreed.LOGGER.warn("[LethalBreed] continuing despite conflict (failOnAiConflict=false) — zombie behaviour may be unpredictable.");
    }

    /** In-session policy: NEVER throw. Throwing from the {@code ENTITY_LOAD} entity pipeline of a running
     *  world crashes mid-tick on a save that is being written (audit #21). Instead log, tell the players, and
     *  if configured to fail, ask the server to stop cleanly — {@code halt(false)}, never {@code halt(true)}:
     *  we are ON the server thread here, and {@code halt(true)} joins that same thread → deadlock. With
     *  {@code false} the current tick finishes and the normal shutdown path saves the world. */
    private static void reportInSession(String detail, ServerLevel level) {
        LethalBreed.LOGGER.error("[LethalBreed] AI CONFLICT — {}", detail);
        if (TargetingConfig.failOnAiConflict) {
            level.getServer().getPlayerList().broadcastSystemMessage(
                    Component.literal("§c[LethalBreed] Incompatible zombie-AI mod detected (" + detail
                            + ") — stopping the server. Remove it, or set failOnAiConflict=false.")
                            .withStyle(ChatFormatting.RED),
                    false);
            level.getServer().halt(false);
        } else {
            LethalBreed.LOGGER.warn("[LethalBreed] continuing despite conflict (failOnAiConflict=false) — zombie behaviour may be unpredictable.");
        }
    }
}
