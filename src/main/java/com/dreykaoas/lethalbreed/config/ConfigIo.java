package com.dreykaoas.lethalbreed.config;

import com.dreykaoas.lethalbreed.LethalBreed;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

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
        try {
            if (Files.exists(path)) {
                String text = Files.readString(path);
                JsonObject json = JsonParser.parseString(text).getAsJsonObject();
                int applied = 0;
                for (Field f : ConfigFields.all()) {
                    if (json.has(f.getName())) {
                        if (ConfigFields.apply(f.getName(), json.get(f.getName()).getAsString(), false)) {
                            applied++;
                        }
                    }
                }
                LethalBreed.LOGGER.info("[LethalBreed] config loaded ({} options) from {}", applied, path);
            } else {
                LethalBreed.LOGGER.info("[LethalBreed] no config file — writing defaults to {}", path);
            }
        } catch (Exception e) {
            LethalBreed.LOGGER.warn("[LethalBreed] config load failed ({}): keeping defaults", e.toString());
        }
        // Always (re)write so the file is complete and reflects newly-added options.
        save();
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
                }
            } catch (IllegalAccessException ignored) {
            }
        }
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(json));
        } catch (IOException e) {
            LethalBreed.LOGGER.warn("[LethalBreed] config save failed: {}", e.toString());
        }
    }
}
