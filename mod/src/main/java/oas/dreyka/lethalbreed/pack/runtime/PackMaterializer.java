package oas.dreyka.lethalbreed.pack.runtime;

import oas.dreyka.lethalbreed.GameState;
import oas.dreyka.lethalbreed.config.domain.PackConfig;
import oas.dreyka.lethalbreed.config.domain.WorldSpawnConfig;
import oas.dreyka.lethalbreed.entity.SmartZombie;
import oas.dreyka.lethalbreed.phase.PhaseManager;
import oas.dreyka.lethalbreed.pack.rule.PackMigrationGate;
import oas.dreyka.lethalbreed.pack.PackState;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ListIterator;
import java.util.Random;

/**
 * Turns a pack that nobody can see into a point on the map, and back again.
 *
 * <p><b>The risk this class exists to manage is duplication, and duplication here is permanent.</b> The mod
 * calls {@code setPersistenceRequired} on every zombie, so nothing ever despawns: a copy created once burns
 * AI budget forever, which slows chunk unloading, which creates more copies. The damage compounds, and
 * nothing about it is cosmetic. The chunk window cannot be raced either. This project has measured unload
 * delays of 2, 35, 272 and once over 1200 ticks. So the approach is not to be fast; it is to make each
 * transition atomic and to check, every single time, whether the member is already back in the world
 * before creating it.
 *
 * <p>Three guards, each covering a failure the others cannot:
 * <ol>
 *   <li>{@link #alreadyPresent}: the world is asked whether that exact UUID exists before any respawn. This
 *       is the only defence against a stale disk copy coming back on its own.</li>
 *   <li>The {@code ENTITY_UNLOAD} net in {@code EntityEventsInit}: when the chunk beats us to it, the member
 *       is counted as detached and <b>no ghost is written</b>. A pack that loses stragglers at chunk
 *       borders can be repaired; a pack that duplicates one cannot.</li>
 *   <li>The phase is consulted before restoring: the same condition {@code SpawnFilter.shouldCull}
 *       applies at ENTITY_LOAD. In phase 0 every hostile is destroyed
 *       at ENTITY_LOAD, silently. The pack would keep marching with members that are deleted on arrival.</li>
 * </ol>
 */
public final class PackMaterializer {
    private PackMaterializer() {}

    /** One sweep for one pack. Cheap by construction: a position test, not a scan of the population. */
    public static void tick(ServerLevel level, PackState pack, long gameTime) {
        BlockPos centre = BlockPos.containing(pack.x, level.getSeaLevel(), pack.z);
        boolean ticking = level.isPositionEntityTicking(centre);
        if (pack.phase == PackState.Phase.MATERIAL) {
            dematerialiseIfDue(level, pack, ticking);
        } else {
            materialiseIfPossible(level, pack, ticking, gameTime);
        }
    }

    private static void dematerialiseIfDue(ServerLevel level, PackState pack, boolean ticking) {
        if (ticking) {
            pack.dematStreak = 0;
            return;
        }
        pack.dematStreak += Math.max(1, PackConfig.packMaterializeInterval);
        if (pack.dematStreak < PackConfig.packDematGraceTicks) {
            return;
        }
        int chunkX = (int) Math.floor(pack.x) >> 4;
        int chunkZ = (int) Math.floor(pack.z) >> 4;
        // Force the chunk for the duration of the snapshot so capture and discard happen on a loaded entity,
        // in one go; holding the force open afterwards would be a permanent pin. setChunkForced answers
        // whether the forced set CHANGED, so true means WE own the release, and false means somebody else
        // (a player's forceload, an arena, another mod) holds it and releasing would unload their area.
        boolean weForcedIt = level.setChunkForced(chunkX, chunkZ, true);
        try {
            // discard() raises ENTITY_UNLOAD in the same call, which drops the member from liveIds.
            int[] roster = pack.liveIds.toIntArray();
            for (int id : roster) {
                SmartZombie sz = GameState.REGISTRY.get(id);
                if (sz == null || !(sz.entity() instanceof Zombie z) || !sz.isValid()) {
                    continue;
                }
                PackState.Ghost ghost = PackSnapshot.capture(level, z);
                if (ghost == null) {
                    continue;   // could not serialise: skip the discard and leave it alive
                }
                pack.ghosts.add(ghost);
                z.discard();
            }
            pack.liveIds.clear();
            pack.phase = PackState.Phase.VIRTUAL;
            pack.dematStreak = 0;
        } finally {
            if (weForcedIt) {
                level.setChunkForced(chunkX, chunkZ, false);
            }
        }
    }

    private static void materialiseIfPossible(ServerLevel level, PackState pack, boolean ticking, long gameTime) {
        if (!ticking) {
            return;
        }
        // Phase 0 destroys every hostile at ENTITY_LOAD: restoring into it would delete the pack silently and
        // leave a point on the map still marching with nobody in it. And bringing one back into full daylight
        // below the immunity phase hands the player a pyre; staying virtual is not a failure, the pack keeps
        // advancing as a point.
        if ((WorldSpawnConfig.nightSpawnEnabled && PhaseManager.current() <= 0)
                || PackMigrationGate.daylightHalt(level)) {
            return;
        }
        Random rng = new Random(pack.seed ^ gameTime);
        int spread = Math.max(1, PackConfig.packSpawnSpread);
        ListIterator<PackState.Ghost> it = pack.ghosts.listIterator();
        while (it.hasNext()) {
            PackState.Ghost ghost = it.next();
            if (alreadyPresent(level, ghost)) {
                it.remove();          // it came back on its own; ENTITY_LOAD re-joins it via the attachment
                continue;
            }
            double x = pack.x + rng.nextInt(2 * spread + 1) - spread;
            double z = pack.z + rng.nextInt(2 * spread + 1) - spread;
            double y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) x, (int) z);
            Entity restored = PackSnapshot.restore(level, ghost, x + 0.5, y, z + 0.5);
            if (restored != null) {
                it.remove();
                continue;
            }
            // A ghost that fails this many consecutive sweeps (e.g. a permanently occluded spawn column) is
            // dropped instead of being retried every sweep for the rest of the world's life.
            int limit = PackConfig.packMaterializeRetries;
            if (limit > 0) {
                int retries = ghost.retries() + 1;
                if (PackState.retriesExhausted(retries, limit)) {
                    it.remove();
                } else {
                    it.set(new PackState.Ghost(ghost.uuidMsb(), ghost.uuidLsb(), ghost.nbt(), retries));
                }
            }
        }
        if (pack.ghosts.isEmpty()) {
            pack.phase = PackState.Phase.MATERIAL;
        }
    }

    /** Is this exact member already in the world? The one question that prevents a permanent duplicate.
     *  Public so the verification rig can assert the guard itself, not just its consequences. */
    public static boolean alreadyPresent(ServerLevel level, PackState.Ghost ghost) {
        return level.getEntity(PackSnapshot.uuidOf(ghost)) != null;
    }
}
