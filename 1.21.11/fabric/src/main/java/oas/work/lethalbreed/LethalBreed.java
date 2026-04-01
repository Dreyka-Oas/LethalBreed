package oas.work.lethalbreed;
import oas.work.lethalbreed.config.ModConfig;
import oas.work.lethalbreed.commands.ReloadCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import static net.minecraft.server.command.CommandManager.literal;

public class LethalBreed implements ModInitializer {
    @Override
    public void onInitialize() {
        ModConfig.load();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("lethalbreed")
                .then(literal("reload").executes(ReloadCommand::run))
            );
        });
    }
}
