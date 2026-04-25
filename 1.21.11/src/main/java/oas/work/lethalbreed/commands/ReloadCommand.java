package oas.work.lethalbreed.commands;

import com.mojang.brigadier.context.CommandContext;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.zombie.Zombie;
import oas.work.lethalbreed.SizeLogic;
import oas.work.lethalbreed.EquipmentLogic;
import oas.work.lethalbreed.config.ModConfig;
import oas.work.lethalbreed.ai.HearingLogic;
import oas.work.lethalbreed.ai.PanicLogic;

public class ReloadCommand {
    public static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel world = source.getServer().overworld();

        ModConfig.reload();
        HearingLogic.reloadConfig();
        PanicLogic.reloadConfig();

        var zombies = world.getEntitiesOfClass(Zombie.class,
            new net.minecraft.world.phys.AABB(-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE,
                                             Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE),
            z -> z.isAlive());

        int count = zombies.size();
        for (Zombie zombie : zombies) {
            SizeLogic.reapplyStats(zombie);
            EquipmentLogic.reequip(zombie);
        }

        source.getPlayer().displayClientMessage(net.minecraft.network.chat.Component.literal("§6[LethalBreed] §aConfig reloaded! §e" + count + " zombies updated."), false);

        return count;
    }
}










