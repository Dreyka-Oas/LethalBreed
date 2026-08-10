package com.dreykaoas.lethalbreed.pack;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.util.ArrayList;
import java.util.List;

/**
 * One pack: where it is, where it is going, and who belongs to it.
 *
 * <p>Mutable by design — this is the per-pack scratch the manager advances every visit, not a value object.
 * It stays free of Minecraft types so the rules around it can be tested without a world; the entities
 * themselves are referenced by runtime id (live) or by UUID plus serialised bytes (dematerialised).
 *
 * <p><b>The membership invariant.</b> A member is in exactly one of three places at any instant:
 * <ul>
 *   <li>{@link #liveIds} — a real entity, ticking</li>
 *   <li>{@link #ghosts} — dematerialised by us, its NBT held here</li>
 *   <li>{@link #detached} — the chunk unloaded before we got to it, so the entity went to disk carrying its
 *       pack attachment, and will re-join when it comes back</li>
 * </ul>
 * Nothing may ever be in two of them. That is the whole defence against duplicating a pack: this mod sets
 * {@code setPersistenceRequired} on every zombie, so a duplicate never despawns and the error compounds.
 *
 * <p>{@link #liveIds} holds runtime entity ids, which change on every chunk reload — it is a cache rebuilt
 * from the persistent attachment, never an identity. Durable identity is the UUID inside a ghost.
 */
public final class PackState {

    /** Whether the pack currently exists as entities or only as a point on the map. */
    public enum Phase { MATERIAL, VIRTUAL }

    /** A dematerialised member: its identity, and its whole entity NBT as bytes.
     *
     *  <p>Bytes rather than a {@code CompoundTag} so this class keeps no Minecraft type; the conversion
     *  happens in the world-side snapshot code. The UUID is kept out of the blob as well as in it, so the
     *  "is this one already back in the world?" check costs nothing to answer.
     *
     *  <p>{@code retries} counts consecutive failed restore sweeps, so {@code PackMaterializer} can give up
     *  on a ghost that can never come back (e.g. a permanently occluded spawn point) instead of retrying it
     *  every sweep forever. Defaults to 0 for freshly captured ghosts and for ghosts saved before this field
     *  existed. */
    public record Ghost(long uuidMsb, long uuidLsb, byte[] nbt, int retries) {
        public Ghost(long uuidMsb, long uuidLsb, byte[] nbt) {
            this(uuidMsb, uuidLsb, nbt, 0);
        }
    }

    /** Whether a ghost that has now failed {@code retries} consecutive restore sweeps should be dropped
     *  rather than tried again next sweep. {@code limit <= 0} disables the cutoff — retried forever, which
     *  is the behaviour from before this cutoff existed. */
    public static boolean retriesExhausted(int retries, int limit) {
        return limit > 0 && retries >= limit;
    }

    public long id;
    /** Pack position. XZ only: Y is meaningless for a pack that flies over terrain, and is resolved against
     *  the real heightmap at materialisation. */
    public double x;
    public double z;
    public int destX;
    public int destZ;
    /** Unit heading, carried across destinations. This persistence is what makes the path a migration. */
    public double headingX = 1.0;
    public double headingZ;
    /** Seed of this pack's own random stream, persisted so a reload reproduces the same wandering. */
    public long seed;
    public Phase phase = Phase.MATERIAL;
    /** Game time at which the pause on arrival ends. */
    public long dwellUntil;
    /** Game time of the last virtual advance, so the next one can prorate by the ticks actually elapsed. */
    public long lastAdvanceTick;
    /** Game time from which the pack has been under the minimum size, or 0 while it is healthy. */
    public long belowMinSince;
    /** Consecutive observations of "no longer entity-ticking", for the dematerialisation grace period. */
    public int dematStreak;
    /** Activations without headway, before the pack gives up on an unreachable destination. */
    public int stuckActivations;
    /** Distance to the destination at the previous visit, to tell headway from stalling. */
    public double lastDistToDest = Double.MAX_VALUE;
    /** Members that went to disk with the chunk before we could snapshot them. */
    public int detached;

    public final IntArrayList liveIds = new IntArrayList(8);
    public final List<Ghost> ghosts = new ArrayList<>(0);

    public PackState(long id, double x, double z, long seed) {
        this.id = id;
        this.x = x;
        this.z = z;
        this.seed = seed;
        this.destX = (int) Math.round(x);
        this.destZ = (int) Math.round(z);
    }

    /** Everyone the pack still counts as its own, whichever of the three states they are in. */
    public int totalMembers() {
        return liveIds.size() + ghosts.size() + Math.max(0, detached);
    }

    /** True once the pack has nobody left anywhere — the manager drops it without waiting for any grace. */
    public boolean isEmpty() {
        return totalMembers() == 0;
    }
}
