package oas.work.lethalbreed.config;

import com.google.gson.*;
import oas.work.lethalbreed.ModLogger;
import oas.work.lethalbreed.config.model.*;
import net.fabricmc.loader.api.FabricLoader;
import java.io.*;
import java.nio.file.*;

public class ModConfig {
    public Attributes attributes = new Attributes();
    public Mutant mutant = new Mutant();
    public Equipment equipment = new Equipment();
    public AI ai = new AI();
    public Panic panic = new Panic();
    public Movement movement = new Movement();
    public Breaking breaking = new Breaking();

    public static ModConfig INSTANCE = new ModConfig();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("o.a.s");
    private static final File FILE = DIR.resolve("lethalbreed.json").toFile();

    public static void load() {
        try {
            if (!Files.exists(DIR)) Files.createDirectories(DIR);
            if (!FILE.exists()) { save(new ModConfig()); return; }
            
            try (FileReader reader = new FileReader(FILE)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                if (!ConfigValidator.isValid(json, ModConfig.class)) save(new ModConfig());
                else INSTANCE = GSON.fromJson(json, ModConfig.class);
            }
        } catch (Exception e) {
            ModLogger.error("Config load failed: " + e.getMessage());
            save(new ModConfig());
        }
    }

    public static void save(ModConfig config) {
        INSTANCE = config;
        try (FileWriter writer = new FileWriter(FILE)) { GSON.toJson(config, writer); }
        catch (Exception e) { ModLogger.error("Config save failed: " + e.getMessage()); }
    }

    public static void reload() {
        load();
        ModLogger.info("Config reloaded from disk");
    }
}





