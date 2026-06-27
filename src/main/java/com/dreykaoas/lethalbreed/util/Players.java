package com.dreykaoas.lethalbreed.util;

import com.dreykaoas.lethalbreed.config.LethalBreedConfig;
import net.minecraft.world.entity.player.Player;

/** Whether a player should be perceived/chased by zombies (creative/spectator excluded by default). */
public final class Players {
    private Players() {}

    public static boolean isTargetable(Player p) {
        if (LethalBreedConfig.targetCreativePlayers) {
            return true;
        }
        return !p.isCreative() && !p.isSpectator();
    }
}
