package com.dreykaoas.lethalbreed.pack.runtime;

import com.dreykaoas.lethalbreed.config.domain.PackConfig;
import com.dreykaoas.lethalbreed.pack.PackAdvance;
import com.dreykaoas.lethalbreed.pack.PackManager;
import com.dreykaoas.lethalbreed.pack.PackState;

import net.minecraft.server.level.ServerLevel;

/**
 * Advances a pack that has no bodies left to walk it: a point sliding across the map.
 *
 * <p>This is the whole point of dematerialising. A pack out of everyone's sight costs one
 * {@link PackAdvance#step} every {@code packMaterializeInterval} ticks instead of N zombies' worth of
 * pathfinding, collision and block queries — and it keeps travelling, which is what "il reste toujours
 * chargé" ("it stays loaded the whole time") asks for.
 *
 * <p><b>No terrain is read, deliberately.</b> Reading a block in an ungenerated chunk forces that chunk to
 * generate, which is the cost trap that kills this kind of system: a pack crossing 2000 blocks would generate
 * a corridor of world nobody asked for. The virtual pack therefore flies over oceans and mountains alike, and
 * is put back on the real surface only when it materialises. The honest corollary is that a "packAvoidWater"
 * option cannot be built on top of this without giving that up.
 */
public final class PackVirtualMove {
    private PackVirtualMove() {}

    public static void tick(ServerLevel level, PackManager manager, PackState pack, long gameTime) {
        if (!PackConfig.packMigrationEnabled) {
            return;
        }
        if (!PackConfig.packMigrateAtDay && level.isBrightOutside()) {
            return;
        }
        if (gameTime < pack.dwellUntil) {
            return;
        }
        // Prorate by the ticks actually elapsed. Packs are visited round-robin, so without this a pack's
        // speed would depend on how many other packs happen to exist — the world would slow down as it
        // filled up, which is exactly the kind of coupling that makes a simulation impossible to reason about.
        long elapsed = pack.lastAdvanceTick == 0L ? 1L : Math.max(0L, gameTime - pack.lastAdvanceTick);
        double[] pos = {pack.x, pack.z};
        boolean arrived = PackAdvance.step(pos, pack.destX, pack.destZ,
                PackConfig.packVirtualSpeed, elapsed, PackConfig.packArriveDistance);
        pack.x = pos[0];
        pack.z = pos[1];
        pack.lastAdvanceTick = gameTime;
        if (arrived) {
            chooseNext(level, pack, gameTime);
        }
    }

    private static void chooseNext(ServerLevel level, PackState pack, long gameTime) {
        PackDestinationPick.pick(level, pack);
        pack.dwellUntil = gameTime + Math.max(0, PackConfig.packDwellTicks);
    }
}
