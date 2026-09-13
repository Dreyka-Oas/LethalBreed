package oas.dreyka.lethalbreed.pack.rule;

import oas.dreyka.lethalbreed.config.domain.PackConfig;
import oas.dreyka.lethalbreed.pack.PackState;

import net.minecraft.server.level.ServerLevel;

/**
 * The one question both movement paths ask before they touch a pack: may it travel this tick?
 *
 * <p>A materialised pack and a dematerialised one answer it identically, and they have to. A pack that
 * crossed ten chunks while nobody was looking and then stood still the moment a player arrived would make
 * the dematerialisation visible, which is the only thing it must never be.
 */
public final class PackMigrationGate {
    private PackMigrationGate() {}

    /** True when the pack must not advance: migration off, daylight halt, or still serving its dwell. */
    public static boolean halted(ServerLevel level, PackState pack, long gameTime) {
        if (!PackConfig.packMigrationEnabled) {
            return true;
        }
        if (daylightHalt(level)) {
            return true;
        }
        return gameTime < pack.dwellUntil;
    }

    /**
     * The daylight half on its own, for the caller that materialises rather than moves.
     *
     * <p>Members keep whatever waypoint they hold across the halt. Mood clears it when it puts them to
     * sleep, via the FROZEN path: replanting during the day would fight the day-sleep for the whole day.
     */
    public static boolean daylightHalt(ServerLevel level) {
        return !PackConfig.packMigrateAtDay && level.isBrightOutside();
    }
}
