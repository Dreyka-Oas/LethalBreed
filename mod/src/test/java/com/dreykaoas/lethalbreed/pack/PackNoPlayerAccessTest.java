package com.dreykaoas.lethalbreed.pack;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nothing under {@code pack/} may reach for a player.
 *
 * <p>This is the one hard rule of the whole mechanic: a pack decides where to go from its own heading and
 * its own seeded randomness, never from where anybody is standing. Break it and the feature silently becomes
 * "the horde always knows where you are", which is the opposite of what it is for — and nothing else would
 * fail. No unit test on the rules can catch it, because the leak would be in the plumbing that feeds them.
 *
 * <p>So the guard is on the source text. A comment saying "do not read players here" does not survive six
 * months of edits; a failing build does.
 *
 * <p>Checked against the SOURCE tree, like {@code MixinConfigTest}: the question is what the code is allowed
 * to mention, which the files answer directly and the classpath does not.
 *
 * <p><b>Deliberately NOT banned:</b> {@code ServerLevel} as a whole. The materialiser has to ask the level
 * whether a position is still entity-ticking — that is a chunk-status question, with no player in it. Only
 * the specific ways of obtaining a player are forbidden.
 */
class PackNoPlayerAccessTest {

    private static final Path PACK_SRC = Path.of("src/main/java/com/dreykaoas/lethalbreed/pack");

    /** Each entry: the forbidden fragment, and why it is forbidden. */
    private static final String[][] FORBIDDEN = {
            {"ServerPlayer", "a pack must never hold or inspect a player entity"},
            {"net.minecraft.world.entity.player", "same, via the player package"},
            {"util.Players", "the targetable-player helper is a player lookup by another name"},
            {"getNearestPlayer", "distance-to-player is exactly the input a migration may not have"},
            {"getPlayerByUUID", "any way of resolving a player is a way of reading its position"},
            {".players()", "the level's player list"},
            {"FlowFieldManager", "the flow field is seeded on players — using it reads them transitively"},
    };

    @Test
    void noFileUnderPackReachesForAPlayer() throws IOException {
        assertTrue(Files.isDirectory(PACK_SRC), "pack sources not found at " + PACK_SRC.toAbsolutePath());
        List<String> offences = new ArrayList<>();
        try (Stream<Path> files = Files.walk(PACK_SRC)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String body = Files.readString(f);
                for (String[] rule : FORBIDDEN) {
                    if (body.contains(rule[0])) {
                        offences.add(PACK_SRC.relativize(f) + " mentions '" + rule[0] + "' — " + rule[1]);
                    }
                }
            }
        }
        assertTrue(offences.isEmpty(),
                "la migration ne doit jamais lire une position de joueur :\n  " + String.join("\n  ", offences));
    }

    /** The guard is worthless if it scans nothing — a moved or renamed package would make it vacuously
     *  green. Same reasoning as {@code ChunkCycles} failing on zero observed round-trips. */
    @Test
    void theGuardActuallyScannedSomething() throws IOException {
        try (Stream<Path> files = Files.walk(PACK_SRC)) {
            long n = files.filter(p -> p.toString().endsWith(".java")).count();
            assertTrue(n >= 4, "seulement " + n + " fichier(s) scanné(s) sous " + PACK_SRC);
        }
    }
}
