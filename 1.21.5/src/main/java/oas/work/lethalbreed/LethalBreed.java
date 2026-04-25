package oas.work.lethalbreed;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.common.MinecraftForge;
import oas.work.lethalbreed.commands.ReloadCommand;
import oas.work.lethalbreed.config.ModConfig;

import static net.minecraft.commands.Commands.literal;

@Mod(ModConstants.MOD_ID)
public class LethalBreed {
    public LethalBreed() {
        ModConfig.load();
        MinecraftForge.EVENT_BUS.addListener(this::registerCommands);
    }

    private void registerCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(literal("lethalbreed")
            .then(literal("reload").executes(ReloadCommand::run))
        );
    }
}







