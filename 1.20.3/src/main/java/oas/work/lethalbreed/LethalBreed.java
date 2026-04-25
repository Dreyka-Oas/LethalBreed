package oas.work.lethalbreed;

import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import oas.work.lethalbreed.config.ModConfig;

@Mod("lethalbreed")
public class LethalBreed {
    public LethalBreed(net.neoforged.bus.api.IEventBus eventBus) {
        ModConfig.load();
        NeoForge.EVENT_BUS.register(LethalBreedEvents.class);
    }
}