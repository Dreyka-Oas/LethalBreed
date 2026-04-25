package oas.work.lethalbreed.util;

import net.minecraft.world.entity.player.Player;

public class MessageUtils {
    
    public static void sendOverlay(Player player, String message) {
        if (player != null && !player.level().isClientSide()) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(message), true);
        }
    }

    public static void sendChat(Player player, String message) {
        if (player != null) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(message), false);
        }
    }
}









