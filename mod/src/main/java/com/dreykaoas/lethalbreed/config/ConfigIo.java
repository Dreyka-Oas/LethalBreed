package com.dreykaoas.lethalbreed.config;

import net.fabricmc.loader.api.FabricLoader;

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
 *
 * <p>This class is now only the facade plus the one thing its collaborators must not know: WHERE the file
 * lives. Each concern is a leaf — {@link ConfigLoader} (read + apply), {@link ConfigWriter} (atomic write),
 * {@link ConfigQuarantine} (archive an unusable file), {@link ConfigDriftReport} (tell the operator). They
 * take the path as a parameter, which is what lets the writer be tested against a temp directory with no
 * Fabric runtime present.
 */
public final class ConfigIo {
    private ConfigIo() {}

    /** Structure check from the last load. Volatile because load() runs on the main thread at boot but
     *  the join notice and /lethalconfig verify read it from the server thread. */
    private static volatile ConfigStructure.Report lastReport;

    /** {@code config/oas/lethalbreed.json}. The "oas" folder is the author's namespace. */
    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("oas").resolve("lethalbreed.json");
    }

    public static void load() {
        lastReport = ConfigLoader.load(file());
    }

    public static synchronized void save() {
        ConfigWriter.save(file());
    }

    /** The structure check from the most recent {@link #load()}, or null if the config has not been
     *  read yet. Read by {@code /lethalconfig verify} and the operator join notice — a log line alone
     *  is close to worthless to a solo player, who never opens latest.log. */
    public static ConfigStructure.Report lastReport() {
        return lastReport;
    }
}
