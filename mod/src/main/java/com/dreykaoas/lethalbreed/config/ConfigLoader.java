package com.dreykaoas.lethalbreed.config;

import com.dreykaoas.lethalbreed.LethalBreed;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Reading {@code lethalbreed.json} into the live config fields, and telling the operator what is wrong with
 * the file's shape.
 *
 * <p>Split out of {@code ConfigIo}, which held loading, writing, archiving and reporting in one 309-line
 * class. Takes the path as a parameter rather than resolving it, so the read path is exercisable without a
 * Fabric runtime.
 *
 * @return the structure report, or {@code null} when the file was absent or had to be quarantined.
 */
public final class ConfigLoader {
    private ConfigLoader() {}

    public static ConfigStructure.Report load(Path path) {
        if (!Files.exists(path)) {
            LethalBreed.LOGGER.info("[LethalBreed] no config file — writing defaults to {}", path);
            ConfigWriter.save(path);
            return null;
        }
        ConfigStructure.Report report;
        try {
            String text = Files.readString(path);
            JsonObject json = JsonParser.parseString(text).getAsJsonObject();
            // Accept both the current nested layout (each category is a JsonObject holding its options) and
            // the old flat pre-migration layout (every option directly on the root). The file on disk has
            // been flat until this change, so a flat file is what every existing user has; if we only
            // understood the nested shape, the first launch after the migration would read nothing, silently
            // fall back to the 305 code defaults, and the save() below would immediately overwrite the file
            // with those defaults — destroying every setting the user changed, with no error and no warning.
            // Flatten one level deep into a name→value map so both shapes (and any half-migrated mix) resolve
            // through the same per-field loop below. When a name appears both at the root and inside a
            // category, the nested value wins: nested is the current format.
            Map<String, JsonElement> values = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                if (entry.getValue().isJsonObject()) {
                    continue;
                }
                values.put(entry.getKey(), entry.getValue());
            }
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                if (entry.getValue().isJsonObject()) {
                    for (Map.Entry<String, JsonElement> inner : entry.getValue().getAsJsonObject().entrySet()) {
                        values.put(inner.getKey(), inner.getValue());
                    }
                }
            }

            // Check the file's SHAPE before applying anything, so we can tell the user what is wrong
            // instead of silently dropping it. The loop below is field-driven, not file-driven — it
            // never looks at a key the schema does not have — so without this a misspelled option is
            // invisible: the edit does nothing, the summary line still reports success, and save()
            // deletes the line. The user watches their edit vanish with no explanation.
            Set<String> knownNames = new HashSet<>();
            for (Field f : ConfigFields.all()) {
                knownNames.add(f.getName());
            }
            report = ConfigStructure.check(json, knownNames);

            if (report.unusable()) {
                // Content present but not one key of it recognisable. Anything less than this and
                // rewriting would throw away the settings that ARE still readable, so this is the only
                // structural condition that justifies starting over.
                if (ConfigQuarantine.moveAside(path, report.keysInFile() + " keys, none of them a known option")) {
                    ConfigWriter.save(path);
                }
                // Deliberately NOT the report: it describes the file we just moved aside. The config now
                // running is pristine defaults, so there is nothing for /lethalconfig verify or the operator
                // join notice to nag about — and nagging about a file that no longer exists, for the rest of
                // the session, is worse than saying nothing.
                return null;
            }
            ConfigDriftReport.emit(report, path);

            int applied = 0;
            int ignored = 0;
            for (Field f : ConfigFields.all()) {
                if (!values.containsKey(f.getName())) {
                    continue;
                }
                // One bad field must not cost the user every field after it: getAsString() throws on a
                // JSON object or null (neither overrides JsonElement.getAsString()), and that exception
                // used to escape the loop entirely, leaving the rest at code defaults — which save() then
                // persisted. Guard per field, and account for what was dropped instead of staying silent.
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
            if (ConfigQuarantine.moveAside(path, e.toString())) {
                ConfigWriter.save(path);
            }
            return null;
        }
        // Read succeeded: (re)write so the file is complete and reflects newly-added options.
        ConfigWriter.save(path);
        return report;
    }
}
