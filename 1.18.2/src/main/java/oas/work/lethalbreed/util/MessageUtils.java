package oas.work.lethalbreed.util;

import net.minecraft.world.entity.player.Player;

public class MessageUtils {
    
    public static void sendOverlay(Player player, String message) {
        if (player != null && !player.getLevel().isClientSide()) {
            player.displayClientMessage(new net.minecraft.network.chat.TextComponent(message), true);
        }
    }

    public static void sendChat(Player player, String message) {
        if (player != null) {
            player.displayClientMessage(new net.minecraft.network.chat.TextComponent(message), false);
        }
    }
}







