package com.dreykaoas.lethalbreed.pack.runtime;

import com.dreykaoas.lethalbreed.pack.PackManager;
import com.dreykaoas.lethalbreed.pack.PackState;
import com.dreykaoas.lethalbreed.pack.PackWander;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.border.WorldBorder;

/**
 * Shared plumbing between {@link PackMarch} and {@link PackVirtualMove}: both need a fresh heading/destination
 * from {@link PackWander#next} built off the current world border, and both write it onto the pack the same
 * way. Only the two callers' dwell-time bookkeeping differs, so that part stays with each of them.
 */
final class PackDestinationPick {
    private PackDestinationPick() {}

    /** Pick a new destination/heading for {@code pack} at its current position and write destX/destZ/headingX/
     *  headingZ onto it. Does not touch {@code dwellUntil} or anything else — callers own that. */
    static void pick(ServerLevel level, PackState pack) {
        WorldBorder border = level.getWorldBorder();
        double[] heading = new double[2];
        PackWander.Destination next = PackWander.next(
                (int) Math.round(pack.x), (int) Math.round(pack.z), pack.headingX, pack.headingZ,
                (int) Math.floor(border.getMinX()), (int) Math.floor(border.getMinZ()),
                (int) Math.ceil(border.getMaxX()), (int) Math.ceil(border.getMaxZ()),
                PackManager.rngFor(pack), heading);
        pack.destX = next.x();
        pack.destZ = next.z();
        pack.headingX = heading[0];
        pack.headingZ = heading[1];
    }
}
