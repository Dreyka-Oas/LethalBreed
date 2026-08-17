package com.dreykaoas.lethalbreed.dev.arena.pack;

import com.dreykaoas.lethalbreed.GameState;
import com.dreykaoas.lethalbreed.dev.arena.ArenaBuilder;
import com.dreykaoas.lethalbreed.entity.SmartZombie;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/**
 * Geometry and population for the {@code pack} suite.
 *
 * <p><b>No fake player here, unlike most rigs.</b> A targetable player would give every zombie a real hunt
 * target, and a hunt target beats a pack waypoint by design — the arena would measure the aggro path and
 * report nothing at all about packs. The corridor is force-loaded instead, which is what lets it tick with
 * nobody in it.
 */
public final class PackArena {
    private PackArena() {}

    /** Free column: 30, 90, 150, 210 and 400 are taken by the other rigs. */
    public static final int CX = 600;
    public static final int CZ = ArenaBuilder.VERIFY_BAND_Z;
    public static final int Y = ArenaBuilder.VERIFY_Y;

    /** Long enough that a pack can actually travel and the closing distance means something. */
    public static final int WEST = 20;
    public static final int EAST = 140;

    public static void build(ServerLevel ow) {
        ArenaBuilder.roofedCorridor(ow, CX, CZ, Y, 8, WEST, EAST);
        forceCorridor(ow, true);
    }

    /**
     * Force-load every chunk the corridor spans, not just the 3x3 around its centre.
     *
     * <p>{@code ArenaBuilder.forceChunks} covers 48 blocks; this corridor is 160 long because a migration has
     * to have somewhere to migrate to. Without this, members walking east crossed out of the loaded area,
     * unloaded, left the registry — and the rig stopped sampling, freezing its "closest approach" at whatever
     * the pack had managed inside the first three chunks. The march looked far slower than it was.
     */
    public static void forceCorridor(ServerLevel ow, boolean forced) {
        int chunkZ = CZ >> 4;
        for (int chx = (CX - WEST) >> 4; chx <= (CX + EAST) >> 4; chx++) {
            for (int chz = chunkZ - 1; chz <= chunkZ + 1; chz++) {
                ow.setChunkForced(chx, chz, forced);
            }
        }
    }

    /** A wall right across the corridor. Returns how many blocks it is made of. */
    public static int buildWall(ServerLevel ow, int atX) {
        int placed = 0;
        for (int dz = -8; dz <= 8; dz++) {
            for (int dy = 0; dy <= 3; dy++) {
                ow.setBlock(new BlockPos(atX, Y + dy, CZ + dz), Blocks.STONE.defaultBlockState(), 3);
                placed++;
            }
        }
        return placed;
    }

    /** How much of that wall is still standing. Fewer than were placed means something broke through. */
    public static int wallStanding(ServerLevel ow, int atX) {
        int standing = 0;
        for (int dz = -8; dz <= 8; dz++) {
            for (int dy = 0; dy <= 3; dy++) {
                if (!ow.getBlockState(new BlockPos(atX, Y + dy, CZ + dz)).isAir()) {
                    standing++;
                }
            }
        }
        return standing;
    }

    /** Spawn {@code n} zombies in a line from {@code x0}, one every {@code stepX} blocks. */
    public static List<Zombie> spawnRow(ServerLevel ow, int x0, int stepX, int n) {
        List<Zombie> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Zombie z = ArenaBuilder.spawnZombie(ow, new BlockPos(x0 + i * stepX, Y, CZ));
            if (z != null) {
                z.setPersistenceRequired();
                out.add(z);
            }
        }
        return out;
    }

    /** Remove everything a stage spawned, so the next stage measures its own population and not the leftovers
     *  of the previous one — a pack surviving across stages would silently pass the isolation check. */
    public static void clear(List<Zombie> spawned) {
        for (Zombie z : spawned) {
            z.discard();
        }
        spawned.clear();
    }

    /** How many of these zombies currently carry a pack id, and how many distinct packs that is. */
    public static int distinctPacks(List<Zombie> zombies, java.util.Set<Long> into) {
        into.clear();
        for (Zombie z : zombies) {
            SmartZombie sz = GameState.REGISTRY.get(z.getId());
            if (sz != null && sz.pursuit().pack().inPack()) {
                into.add(sz.pursuit().pack().packId());
            }
        }
        return into.size();
    }

    /** How many of these zombies the mod actually knows about. A zombie missing from the registry is never
     *  handed to PackPass, so it can never join anything — which looks identical, in the verdict, to a rule
     *  that simply declined to form a pack. */
    public static int registered(List<Zombie> zombies) {
        int n = 0;
        for (Zombie z : zombies) {
            if (GameState.REGISTRY.get(z.getId()) != null) {
                n++;
            }
        }
        return n;
    }

    /** Members of {@code zombies} that belong to any pack. */
    public static int packed(List<Zombie> zombies) {
        int n = 0;
        for (Zombie z : zombies) {
            SmartZombie sz = GameState.REGISTRY.get(z.getId());
            if (sz != null && sz.pursuit().pack().inPack()) {
                n++;
            }
        }
        return n;
    }
}
