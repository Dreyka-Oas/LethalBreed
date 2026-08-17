package com.dreykaoas.lethalbreed.pack;

import com.dreykaoas.lethalbreed.config.domain.PackConfig;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import com.dreykaoas.lethalbreed.pack.runtime.PackLifecycle;
import com.dreykaoas.lethalbreed.pack.runtime.PackMembership;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Every pack in one dimension. One instance per {@code WorldAiContext}, exactly like
 * {@code BreachCoordinator} — same shape, same lifetime, same server-thread-only contract.
 *
 * <p><b>The membership invariant, which everything else rests on.</b> A member is in exactly one of three
 * states: a live entity in {@link PackState#liveIds}, a snapshot in {@code ghosts}, or counted in
 * {@code detached} because its chunk unloaded before we reached it. Never two at once. This mod sets
 * {@code setPersistenceRequired} on every zombie, so a duplicate created by breaking that invariant never
 * despawns: it burns AI budget forever, which slows chunk unloading, which creates more duplicates.
 *
 * <p><b>Packs are visited round-robin</b>, {@code packsPerTick} per server tick, not all of them every tick.
 * The per-visit work is bounded by {@code packMaxSize}, so the system's cost is a budget the operator sets
 * rather than a function of how many packs the world happens to have.
 */
public final class PackManager implements PackLifecycle.Registry {

    private final Long2ObjectMap<PackState> packs = new Long2ObjectOpenHashMap<>();
    /** Visit cursor over the id-ordered pack list; a pack created mid-cycle just waits its turn. */
    private int cursor;
    private long nextId = 1L;

    /** Reused by the visit loop — a per-tick allocation here would be paid on the server thread. */
    private final List<PackState> ordered = new ArrayList<>();

    @Override
    public PackState get(long id) {
        return packs.get(id);
    }

    @Override
    public void drop(long id) {
        packs.remove(id);
    }

    @Override
    public boolean holds(long id) {
        return packs.containsKey(id);
    }

    /** Live view for the harness and the debug command. Never mutate through it. */
    public Iterable<PackState> all() {
        return packs.values();
    }

    /**
     * Create a pack around one founding member.
     *
     * <p>The seed comes from the id, so a pack's wandering is reproducible across a reload — the same pack
     * always draws the same route, which makes an odd path reportable instead of a one-off.
     */
    public PackState form(SmartZombie founder) {
        long id = nextId++;
        PackState pack = new PackState(id, founder.x(), founder.z(), id * 0x9E3779B97F4A7C15L);
        packs.put(id, pack);
        PackMembership.join(founder, pack, this::get);
        return pack;
    }

    public void join(SmartZombie sz, PackState pack) {
        PackMembership.join(sz, pack, this::get);
    }

    public void leave(SmartZombie sz) {
        PackMembership.leave(sz, get(sz.pursuit().pack().packId()));
    }

    /** A member came back from disk carrying a pack id. False → the pack is gone, or it reloaded too far from
     *  where the pack now is, and the tag was cleared either way. */
    public boolean rejoin(SmartZombie sz, long packId) {
        PackState pack = get(packId);
        if (pack != null) {
            double dx = sz.x() - pack.x;
            double dz = sz.z() - pack.z;
            if (PackJoinRule.outsideRejoinRadius(dx * dx + dz * dz, PackConfig.packRejoinRadius)) {
                // Too far from the pack's current position to still belong to it. It was counted `detached`
                // while it sat on disk; release that count here too, or the pack would keep believing a
                // member exists that will now never tick, ghost, or detach again.
                pack.detached = Math.max(0, pack.detached - 1);
                PackMembership.leave(sz, null);
                return false;
            }
        }
        return PackMembership.rejoin(sz, pack);
    }

    /** A member's chunk unloaded before we could snapshot it. See {@link PackMembership#detach}. */
    public void detach(SmartZombie sz) {
        PackState pack = get(sz.pursuit().pack().packId());
        if (pack != null) {
            PackMembership.detach(pack, sz.id());
        }
    }

    /** Visit the next {@code packsPerTick} packs: recentroid, dissolve if spent, otherwise try to merge. */
    public void tick(long gameTime) {
        if (!PackConfig.packEnabled || packs.isEmpty()) {
            return;
        }
        ordered.clear();
        ordered.addAll(packs.values());
        ordered.sort((a, b) -> Long.compare(a.id, b.id));

        int visits = Math.min(Math.max(1, PackConfig.packsPerTick), ordered.size());
        for (int i = 0; i < visits; i++) {
            PackState pack = ordered.get(cursor++ % ordered.size());
            PackLifecycle.recentroid(pack);
            if (PackLifecycle.dissolveIfSpent(pack, gameTime, this)) {
                continue;
            }
            PackLifecycle.merge(pack, ordered, this, this::get);
        }
        cursor %= Math.max(1, ordered.size());
    }

    /** The id the next pack will take, so persistence can resume the sequence instead of reusing ids. */
    public long nextId() {
        return nextId;
    }

    /**
     * Adopt a persisted set of packs at world load.
     *
     * <p>Their {@code liveIds} are left empty on purpose: runtime entity ids are reassigned every reload, so
     * the live roster is rebuilt as members come back and re-join through their attachment. What is restored
     * is the part nothing else owns — route, seed, ghosts, and the detached count.
     */
    public void restore(Iterable<PackState> saved, long nextId) {
        packs.clear();
        for (PackState p : saved) {
            packs.put(p.id, p);
        }
        this.nextId = Math.max(1L, nextId);
        this.cursor = 0;
    }

    /** This pack's own random stream. Re-derived per destination rather than kept as a field, so a reload
     *  reproduces the route instead of restarting from a fresh sequence. */
    public static Random rngFor(PackState pack) {
        return new Random(pack.seed ^ pack.destX ^ ((long) pack.destZ << 32));
    }
}
