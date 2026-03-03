/**
 * Project: Lethal Breed
 * Responsibility: Messaging Utilities (Overlay/Chat)
 * License: O.A.S - MS-RSL (Microsoft Reference Source License)
 * Copyright (c) 2026 O.A.S (Optimization & Quality). All rights reserved.
 */
package oas.work.lethalbreed.util;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

public class MessageUtils {
    
    public static void sendOverlay(PlayerEntity player, String message) {
        if (player != null && !player.getEntityWorld().isClient()) {
             // true = overlay (action bar)
            player.sendMessage(Text.literal(message), true);
        }
    }

    public static void sendChat(PlayerEntity player, String message) {
        if (player != null) {
            player.sendMessage(Text.literal(message), false);
        }
    }
}
