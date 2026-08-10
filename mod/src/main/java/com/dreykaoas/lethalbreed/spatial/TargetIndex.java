package com.dreykaoas.lethalbreed.spatial;

import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Spatial index of PREY — every living entity a zombie may hunt — one per dimension.
 *
 * <p><b>Why this exists.</b> Target acquisition used to ask the world:
 * {@code getEntitiesOfClass(LivingEntity.class, box, isValid)} over an 80-block box. That visits every
 * entity in the box and runs the predicate on each — and the predicate rejects {@code Zombie}, which in
 * this mod is nearly everything in the box. So each zombie paid for walking the whole horde in order to
 * discard it: O(zombies²) per bucket cycle. Measured with {@code StageProfiler} at ~22us per activation,
 * 50% of the reclassify stage and ~20% of the mod's entire tick budget, at only ~100 zombies.
 *
 * <p>Indexing the prey instead makes a query cost O(cells probed + prey nearby), independent of how large
 * the horde grows. Zombies are never inserted, so they are never visited.
 *
 * <p><b>Players are deliberately NOT indexed.</b> They are queried live from the level at lookup time.
 * A player is the highest-stakes target in the game, and there is no acceptable failure mode where a
 * bookkeeping slip makes one invisible to the horde; there are also never more than a handful, so
 * scanning them directly costs nothing. This index only holds the cheap-to-lose, numerous prey.
 *
 * <p><b>Lifetime.</b> Entries hold live {@link LivingEntity} references, which pin the entity, its level
 * and the server. They are dropped on {@code ENTITY_UNLOAD}, again defensively in {@link #refresh()} for
 * anything that died or was removed without the event reaching us, and wholesale when the per-dimension
 * context is discarded at {@code SERVER_STOPPED}. That belt-and-braces shape is deliberate: the same
 * structure without it is exactly the leak that audit finding P7-1 was.
 */
public final class TargetIndex {

    /**
     * Cell width in blocks. Larger than the zombie grid's 8 on purpose: target queries use
     * {@code targetDetectRadius} (40 by default) rather than a melee-scale radius, and the probe count
     * grows as {@code ((2*radius/cell)+1)²} — 121 cells at 8 blocks against 36 at 16. Prey are sparse
     * enough that the coarser bucket costs nothing on the filtering side.
     */
    private static final int CELL = 16;

    private final Long2ObjectMap<List<LivingEntity>> cells = new Long2ObjectOpenHashMap<>();
    private final Int2ObjectMap<LivingEntity> tracked = new Int2ObjectOpenHashMap<>();
    private final Int2LongMap cellOf = new Int2LongOpenHashMap();
    private final List<LivingEntity> stale = new ArrayList<>();

    public TargetIndex() {
        cellOf.defaultReturnValue(Long.MIN_VALUE);
    }

    private static long packKey(int cx, int cz) {
        return CellMath.packKey(cx, cz);
    }

    private static long keyOf(double x, double z) {
        return packKey(Math.floorDiv((int) Math.floor(x), CELL), Math.floorDiv((int) Math.floor(z), CELL));
    }

    /** True for entities this index is responsible for. Zombies are the horde itself, players are queried
     *  live — everything else living is prey and gets indexed. */
    public static boolean indexable(net.minecraft.world.entity.Entity e) {
        return e instanceof LivingEntity && !(e instanceof Zombie) && !(e instanceof Player);
    }

    /** Start tracking a prey entity. Idempotent — a re-load of the same id just re-buckets it. */
    public void track(LivingEntity e) {
        int id = e.getId();
        if (tracked.containsKey(id)) {
            reposition(e);
            return;
        }
        tracked.put(id, e);
        long k = keyOf(e.getX(), e.getZ());
        cells.computeIfAbsent(k, key -> new ArrayList<>(4)).add(e);
        cellOf.put(id, k);
    }

    /** Stop tracking, by id so it works from an unload callback that only has the entity. */
    public void forget(int entityId) {
        LivingEntity e = tracked.remove(entityId);
        if (e == null) {
            return;
        }
        long k = cellOf.remove(entityId);
        if (k != Long.MIN_VALUE) {
            removeFrom(k, e);
        }
    }

    private void removeFrom(long key, LivingEntity e) {
        List<LivingEntity> list = cells.get(key);
        if (list != null) {
            list.remove(e);
            if (list.isEmpty()) {
                cells.remove(key);
            }
        }
    }

    private void reposition(LivingEntity e) {
        int id = e.getId();
        long now = keyOf(e.getX(), e.getZ());
        long was = cellOf.get(id);
        if (was == now) {
            return;
        }
        if (was != Long.MIN_VALUE) {
            removeFrom(was, e);
        }
        cells.computeIfAbsent(now, key -> new ArrayList<>(4)).add(e);
        cellOf.put(id, now);
    }

    /**
     * Re-bucket everything that moved and drop everything that died. Runs once per server tick, and costs
     * O(prey) — NOT O(zombies), which is the whole point: the horde never appears here.
     */
    public void refresh() {
        if (tracked.isEmpty()) {
            return;
        }
        stale.clear();
        for (LivingEntity e : tracked.values()) {
            if (e == null || e.isRemoved() || !e.isAlive()) {
                stale.add(e); // safety net for any removal whose ENTITY_UNLOAD we never saw
            } else {
                reposition(e);
            }
        }
        for (int i = 0; i < stale.size(); i++) {
            LivingEntity e = stale.get(i);
            if (e != null) {
                forget(e.getId());
            }
        }
        stale.clear();
    }

    /**
     * Append every tracked prey whose horizontal distance to (x,z) is within {@code radius} into
     * {@code out}. Does NOT clear {@code out} — the caller composes this with its own player scan. The
     * exact 3D distance and the validity predicate stay with the caller, so this narrows candidates
     * without ever changing which of them is considered a legal target.
     */
    public void collectInto(List<LivingEntity> out, double x, double z, double radius) {
        if (tracked.isEmpty()) {
            return;
        }
        int minCx = CellMath.floorCell(x - radius, CELL);
        int maxCx = CellMath.floorCell(x + radius, CELL);
        int minCz = CellMath.floorCell(z - radius, CELL);
        int maxCz = CellMath.floorCell(z + radius, CELL);
        double r2 = radius * radius;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                List<LivingEntity> list = cells.get(packKey(cx, cz));
                if (list == null) {
                    continue;
                }
                for (int i = 0; i < list.size(); i++) {
                    LivingEntity e = list.get(i);
                    double dx = e.getX() - x;
                    double dz = e.getZ() - z;
                    if (dx * dx + dz * dz <= r2) {
                        out.add(e);
                    }
                }
            }
        }
    }

    /** Drop everything. Called when the dimension's context is discarded. */
    public void clear() {
        cells.clear();
        tracked.clear();
        cellOf.clear();
        stale.clear();
    }

    public int size() {
        return tracked.size();
    }
}
