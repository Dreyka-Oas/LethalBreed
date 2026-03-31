package oas.work.lethalbreed;
import oas.work.lethalbreed.config.ModConfig;
import net.fabricmc.api.ModInitializer;

public class LethalBreed implements ModInitializer {
    @Override
    public void onInitialize() {
        ModConfig.load();
    }
}