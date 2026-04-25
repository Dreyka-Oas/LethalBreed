package oas.work.lethalbreed.util;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

public class MessageUtils {
    
    public static void sendOverlay(PlayerEntity player, String message) {
        if (player != null && !player.getWorld().isClient()) {
            player.sendMessage(Text.literal(message), true);
        }
    }

    public static void sendChat(PlayerEntity player, String message) {
        if (player != null) {
            player.sendMessage(Text.literal(message), false);
        }
    }
}




