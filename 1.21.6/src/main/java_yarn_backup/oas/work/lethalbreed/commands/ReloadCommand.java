package oas.work.lethalbreed.commands;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.mob.ZombieEntity;
import oas.work.lethalbreed.SizeLogic;
import oas.work.lethalbreed.EquipmentLogic;
import oas.work.lethalbreed.config.ModConfig;
import oas.work.lethalbreed.ai.HearingLogic;
import oas.work.lethalbreed.ai.PanicLogic;

public class ReloadCommand {
    public static int run(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerWorld world = source.getServer().getOverworld();

        ModConfig.reload();
        HearingLogic.reloadConfig();
        PanicLogic.reloadConfig();

        var zombies = world.getEntitiesByClass(ZombieEntity.class,
            new net.minecraft.util.math.Box(-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE,
                                             Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE),
            z -> z.isAlive());

        int count = zombies.size();
        for (ZombieEntity zombie : zombies) {
            SizeLogic.reapplyStats(zombie);
            EquipmentLogic.reequip(zombie);
        }

        source.getPlayer().sendMessage(net.minecraft.text.Text.literal("§6[LethalBreed] §aConfig reloaded! §e" + count + " zombies updated."), false);

        return count;
    }
}








