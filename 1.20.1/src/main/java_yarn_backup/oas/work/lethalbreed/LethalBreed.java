package oas.work.lethalbreed;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.MinecraftForge;
import oas.work.lethalbreed.commands.ReloadCommand;
import oas.work.lethalbreed.config.ModConfig;
import net.minecraft.server.command.ServerCommandSource;

import static net.minecraft.server.command.CommandManager.literal;

@Mod(ModConstants.MOD_ID)
public class LethalBreed {
    public LethalBreed() {
        ModConfig.load();
        IEventBus forgeBus = MinecraftForge.EVENT_BUS;
        forgeBus.addListener(this::registerCommands);
    }

    private void registerCommands(RegisterCommandsEvent event) {
        CommandDispatcher<ServerCommandSource> dispatcher = event.getDispatcher();
        dispatcher.register(literal("lethalbreed")
            .then(literal("reload").executes(ReloadCommand::run))
        );
    }
}







