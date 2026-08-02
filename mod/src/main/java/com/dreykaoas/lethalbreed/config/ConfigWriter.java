package com.dreykaoas.lethalbreed.config;

import com.dreykaoas.lethalbreed.LethalBreed;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.TreeMap;

/**
 * Writing every config option back to disk, grouped by GUI category, via an atomic write-then-rename.
 *
 * <p>Split out of {@code ConfigIo}. Takes the destination path as a parameter rather than resolving it, which
 * is what makes the writer testable against a temp directory with no Fabric runtime present.
 */
public final class ConfigWriter {
    private ConfigWriter() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static synchronized void save(Path path) {
        // Group options under their GUI category. Categories are sorted alphabetically so the output is
        // deterministic: the file is rewritten on every launch, and a HashMap's iteration order would
        // produce a spurious diff on every run. Within a category, options keep the schema order that
        // ConfigFields.all() returns — that order is meaningful (grouped by domain class), so it is
        // preserved rather than re-sorted.
        TreeMap<String, JsonObject> byCategory = new TreeMap<>();
        for (Field f : ConfigFields.all()) {
            String category = ConfigCategory.of(f.getName());
            JsonObject json = byCategory.get(category);
            if (json == null) {
                json = new JsonObject();
                byCategory.put(category, json);
            }
            Class<?> t = f.getType();
            try {
                if (t == boolean.class) {
                    json.add(f.getName(), new JsonPrimitive(f.getBoolean(null)));
                } else if (t == int.class) {
                    json.add(f.getName(), new JsonPrimitive(f.getInt(null)));
                } else if (t == long.class) {
                    json.add(f.getName(), new JsonPrimitive(f.getLong(null)));
                } else if (t == double.class) {
                    json.add(f.getName(), new JsonPrimitive(f.getDouble(null)));
                } else if (t == float.class) {
                    json.add(f.getName(), new JsonPrimitive(f.getFloat(null)));
                } else if (t == double[].class) {
                    com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                    for (double v : (double[]) f.get(null)) {
                        arr.add(v);
                    }
                    json.add(f.getName(), arr);
                }
            } catch (IllegalAccessException e) {
                // Not writing an option here makes it vanish from the file on this save and come back as a
                // default on the next load — the user watches a setting disappear with no explanation. Say
                // so; the rest of the file still gets written.
                LethalBreed.LOGGER.error("[LethalBreed] config option {} could not be written to disk",
                        f.getName(), e);
            }
        }
        JsonObject json = new JsonObject();
        for (Map.Entry<String, JsonObject> entry : byCategory.entrySet()) {
            json.add(entry.getKey(), entry.getValue());
        }
        // Write-then-rename rather than writeString(path, …), whose implicit TRUNCATE_EXISTING empties the
        // real file BEFORE the new content is written. That window is short, but ENOSPC turns it into a
        // certainty rather than a race: truncate succeeds, the write does not, and the next start reads a
        // half-written file. A rename is atomic, so readers only ever see the old file or the new one.
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        boolean moved = false;
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(tmp, GSON.toJson(json));
            try {
                Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException notAtomic) {
                // Some filesystems (and any cross-device layout) refuse ATOMIC_MOVE. A plain replace is
                // still strictly better than truncate-in-place.
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } catch (IOException e) {
            LethalBreed.LOGGER.warn("[LethalBreed] config save failed: {}", e.toString());
        } finally {
            if (!moved) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                    // Leaving a stray .tmp is harmless; it is overwritten on the next save.
                }
            }
        }
    }
}
