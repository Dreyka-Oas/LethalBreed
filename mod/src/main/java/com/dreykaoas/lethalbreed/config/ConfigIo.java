package com.dreykaoas.lethalbreed.config;

import com.dreykaoas.lethalbreed.LethalBreed;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * JSON persistence for {@link LethalBreedConfig}, at {@code <gamedir>/config/oas/lethalbreed.json}.
 *
 * <ul>
 *   <li>{@link #load()} on server start: reads the file (if present), overriding the code defaults with
 *       any field it lists, then re-writes the file so it always contains the full, current option set
 *       (new options added by an update appear automatically with their default).</li>
 *   <li>{@link #save()} after any change (command or in-game GUI): writes every option back, pretty-printed.</li>
 * </ul>
 *
 * Values are stored with their natural JSON type (boolean / number) keyed by the exact field name, so the
 * file is human-editable. Reflection via {@link ConfigFields} means no per-field serialization code.
 */
public final class ConfigIo {
    private ConfigIo() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** {@code config/oas/lethalbreed.json}. The "oas" folder is the author's namespace. */
    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("oas").resolve("lethalbreed.json");
    }

    public static void load() {
        Path path = file();
        if (!Files.exists(path)) {
            LethalBreed.LOGGER.info("[LethalBreed] no config file — writing defaults to {}", path);
            save();
            return;
        }
        try {
            String text = Files.readString(path);
            JsonObject json = JsonParser.parseString(text).getAsJsonObject();
            int applied = 0;
            int ignored = 0;
            for (Field f : ConfigFields.all()) {
                if (!json.has(f.getName())) {
                    continue;
                }
                // One bad field must not cost the user every field after it: getAsString() throws on a
                // JSON object or null (neither overrides JsonElement.getAsString()), and that exception
                // used to escape the loop entirely, leaving the rest at code defaults — which save() then
                // persisted. Guard per field, and account for what was dropped instead of staying silent.
                try {
                    JsonElement el = json.get(f.getName());
                    String raw;
                    if (el.isJsonArray()) {
                        // Arrays are stored as a JSON array; primitives as a scalar. Feed apply() the CSV /
                        // string form it parses back (parse() accepts a bracketed or bare comma list).
                        raw = el.toString();
                    } else if (el.isJsonPrimitive()) {
                        raw = el.getAsString();
                    } else {
                        ignored++;
                        continue;
                    }
                    if (ConfigFields.apply(f.getName(), raw, false)) {
                        applied++;
                    } else {
                        ignored++;
                    }
                } catch (Exception perField) {
                    ignored++;
                }
            }
            if (ignored > 0) {
                LethalBreed.LOGGER.warn(
                        "[LethalBreed] config loaded ({} options applied, {} IGNORED — bad type or value) from {}",
                        applied, ignored, path);
            } else {
                LethalBreed.LOGGER.info("[LethalBreed] config loaded ({} options) from {}", applied, path);
            }
        } catch (Exception e) {
            // The whole file is unreadable/unparseable. NEVER fall through to save() here: the in-memory
            // state is the code defaults, and writing it would destroy the user's settings at the exact
            // moment we failed to read them. Move the file aside so its content survives, and only then
            // write a fresh default file.
            if (quarantine(path, e)) {
                save();
            }
            return;
        }
        // Read succeeded: (re)write so the file is complete and reflects newly-added options.
        save();
    }

    /** Move an unparseable config aside so the user keeps its content. Returns true when the original is
     *  safely out of the way and it is therefore sound to write a fresh default file in its place. */
    private static boolean quarantine(Path path, Exception cause) {
        Path aside = path.resolveSibling(path.getFileName() + ".corrupt-"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")));
        try {
            Files.move(path, aside);
            LethalBreed.LOGGER.error(
                    "[LethalBreed] config unreadable ({}) — moved to {} and rewritten with defaults. "
                            + "Fix that file and rename it back to keep your settings.",
                    cause.toString(), aside);
            return true;
        } catch (IOException moveFailed) {
            LethalBreed.LOGGER.error(
                    "[LethalBreed] config unreadable ({}) AND could not be moved aside ({}) — running on "
                            + "defaults, leaving {} untouched.",
                    cause.toString(), moveFailed.toString(), path);
            return false;
        }
    }

    public static synchronized void save() {
        Path path = file();
        JsonObject json = new JsonObject();
        for (Field f : ConfigFields.all()) {
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
            } catch (IllegalAccessException ignored) {
            }
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
