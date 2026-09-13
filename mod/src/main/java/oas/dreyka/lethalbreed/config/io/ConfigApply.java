package oas.dreyka.lethalbreed.config.io;

import oas.dreyka.lethalbreed.LethalBreed;
import oas.dreyka.lethalbreed.config.schema.ConfigFields;

import com.google.gson.JsonElement;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Map;

/**
 * Writing a flattened file into the live config, one schema field at a time.
 *
 * <p>The loop is FIELD-driven, not file-driven: it only ever looks at names the schema has, so
 * {@code ConfigStructure} has to run first to notice anything the file carries that the schema does not.
 *
 * <p>Every field is guarded separately. {@code getAsString()} throws on a JSON object or null, and an
 * exception escaping the loop would leave every remaining option at its code default, which {@code save()}
 * then persists. One bad field must not cost the user every field after it.
 */
final class ConfigApply {
    private ConfigApply() {}

    /** Apply every value the file carries, then log what landed and what was dropped. */
    static void applyAll(Map<String, JsonElement> values, Path path) {
        int applied = 0;
        int ignored = 0;
        for (Field f : ConfigFields.all()) {
            if (!values.containsKey(f.getName())) {
                continue;
            }
            // One bad field must not cost the user every field after it: getAsString() throws on a
            // JSON object or null (neither overrides JsonElement.getAsString()), and an exception let out
            // of the loop would leave the rest at code defaults, which save() then persists. Guard per
            // field, and account for what was dropped instead of staying silent.
            try {
                JsonElement el = values.get(f.getName());
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
                    "[LethalBreed] config loaded ({} options applied, {} IGNORED: bad type or value) from {}",
                    applied, ignored, path);
        } else {
            LethalBreed.LOGGER.info("[LethalBreed] config loaded ({} options) from {}", applied, path);
        }
    }
}
