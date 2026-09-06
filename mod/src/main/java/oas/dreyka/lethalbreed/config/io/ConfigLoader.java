package oas.dreyka.lethalbreed.config.io;

import oas.dreyka.lethalbreed.config.io.diag.ConfigDrift;
import oas.dreyka.lethalbreed.config.io.diag.ConfigDriftReport;
import oas.dreyka.lethalbreed.config.io.diag.ConfigQuarantine;
import oas.dreyka.lethalbreed.config.io.diag.ConfigStructure;
import oas.dreyka.lethalbreed.config.schema.ConfigFields;

import oas.dreyka.lethalbreed.LethalBreed;
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

    public static ConfigDrift.Report load(Path path) {
        if (!Files.exists(path)) {
            LethalBreed.LOGGER.info("[LethalBreed] no config file, writing defaults to {}", path);
            ConfigWriter.save(path);
            return null;
        }
        ConfigDrift.Report report;
        try {
            String text = Files.readString(path);
            JsonObject json = JsonParser.parseString(text).getAsJsonObject();
            // Accept both the current nested layout (each category is a JsonObject holding its options) and
            // the old flat pre-migration layout (every option directly on the root). The file on disk has
            // been flat until this change, so a flat file is what every existing user has; if we only
            // understood the nested shape, the first launch after the migration would read nothing, silently
            // fall back to the 305 code defaults, and the save() below would immediately overwrite the file
            // with those defaults, destroying every setting the user changed, with no error and no warning.
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

            // Check the file's SHAPE before applying anything. Why that matters: see ConfigDriftReport.
            Set<String> knownNames = new HashSet<>();
            for (Field f : ConfigFields.all()) {
                knownNames.add(f.getName());
            }
            report = ConfigStructure.check(json, knownNames);

            if (report.unusable()) {
                // Content present but not one key of it recognisable. Anything less than this and
                // rewriting would throw away the settings that ARE still readable, so nothing weaker
                // justifies starting over.
                if (ConfigQuarantine.moveAside(path, report.keysInFile() + " keys, none of them a known option")) {
                    ConfigWriter.save(path);
                }
                // Deliberately NOT the report: it describes the file we just moved aside. The config now
                // running is pristine defaults, so there is nothing for the operator join notice to nag
                // about. Nagging about a file that no longer exists, for the rest of the session, is
                // worse than saying nothing.
                return null;
            }
            ConfigDriftReport.emit(report, path);

            // Repair the misspellings the check found unambiguous, BEFORE the apply loop: the loop is
            // field-driven and would never look at a key the schema does not have, so without this the
            // user's value is dropped and the write below deletes the line. Moving it onto the real
            // name means the edit takes effect and the file comes out correct, the point being that
            // the file fixes itself and never asks the user to fix it.
            for (ConfigDrift.Rename rename : report.renamed()) {
                JsonElement carried = values.remove(rename.from());
                if (carried != null) {
                    values.put(rename.to(), carried);
                }
            }

            ConfigApply.applyAll(values, path);
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
