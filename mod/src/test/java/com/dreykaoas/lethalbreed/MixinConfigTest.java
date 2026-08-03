package com.dreykaoas.lethalbreed;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every entry of {@code lethalbreed.mixins.json} must name a class that exists.
 *
 * <p>Why this is worth a test rather than a launch: the config declares
 * {@code "injectors": {"defaultRequire": 1}}, so a stale path does not warn — the game refuses to start. That
 * is a slow, opaque way to learn about a rename, and for the CLIENT list it is worse still: a dedicated server
 * never loads those mixins, so a server-side harness run cannot catch a broken client entry at all. Renaming or
 * moving a mixin without updating the config is precisely the mistake this catches, in seconds, with the name
 * printed.
 *
 * <p>Checked against the SOURCE tree rather than the classpath: mixin classes are not on the test compile
 * classpath (they reference remapped Minecraft internals), and the question here is whether the config and the
 * files agree — which the source tree answers directly.
 */
class MixinConfigTest {

    private static final Path SRC = Path.of("src/main/java");
    private static final Path CONFIG = Path.of("src/main/resources/lethalbreed.mixins.json");

    private static JsonObject config() throws IOException {
        assertTrue(Files.exists(CONFIG), "mixin config not found at " + CONFIG.toAbsolutePath());
        return JsonParser.parseString(Files.readString(CONFIG)).getAsJsonObject();
    }

    private static List<String> entries(JsonObject cfg, String key) {
        List<String> out = new ArrayList<>();
        JsonArray arr = cfg.getAsJsonArray(key);
        if (arr != null) {
            arr.forEach(e -> out.add(e.getAsString()));
        }
        return out;
    }

    /** The declared package plus a dotted entry must land on a real .java file. */
    private static Path fileFor(String pkg, String entry) {
        return SRC.resolve((pkg + "." + entry).replace('.', '/') + ".java");
    }

    @Test
    void everyDeclaredMixinExists() throws IOException {
        JsonObject cfg = config();
        String pkg = cfg.get("package").getAsString();
        List<String> missing = new ArrayList<>();
        for (String key : List.of("mixins", "client", "server")) {
            for (String entry : entries(cfg, key)) {
                if (!Files.exists(fileFor(pkg, entry))) {
                    missing.add(key + ": " + entry + " -> " + fileFor(pkg, entry));
                }
            }
        }
        assertTrue(missing.isEmpty(), "mixin config points at classes that do not exist: " + missing);
    }

    /** And the reverse: a mixin file nobody declared is dead weight that silently never applies. */
    @Test
    void everyMixinFileIsDeclared() throws IOException {
        JsonObject cfg = config();
        String pkg = cfg.get("package").getAsString();
        Path root = SRC.resolve(pkg.replace('.', '/'));
        List<String> declared = new ArrayList<>();
        for (String key : List.of("mixins", "client", "server")) {
            declared.addAll(entries(cfg, key));
        }
        List<String> undeclared = new ArrayList<>();
        try (var walk = Files.walk(root)) {
            walk.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                String rel = root.relativize(p).toString().replace('/', '.').replaceAll("\\.java$", "");
                if (!declared.contains(rel)) {
                    undeclared.add(rel);
                }
            });
        }
        assertTrue(undeclared.isEmpty(), "mixin classes present but never declared, so never applied: "
                + undeclared);
    }

    /** defaultRequire=1 is what makes a stale entry fatal instead of silent. Losing it would hide breakage. */
    @Test
    void injectorsStillRequireAtLeastOneMatch() throws IOException {
        JsonObject injectors = config().getAsJsonObject("injectors");
        assertFalse(injectors == null, "injectors block missing");
        assertEquals(1, injectors.get("defaultRequire").getAsInt());
    }
}
