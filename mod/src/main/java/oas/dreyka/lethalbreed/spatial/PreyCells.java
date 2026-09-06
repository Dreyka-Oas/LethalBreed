package oas.dreyka.lethalbreed.spatial;

import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * The bucket half of {@link TargetIndex}: which cell each prey entity currently sits in, and the list of
 * entities per cell.
 *
 * <p>Split from the index itself because nothing else owns invariants across two maps at once (an entity's
 * id in {@code cellOf} and the same entity in the {@code cells} list for that key), and every leak this
 * structure has ever had came from those two drifting apart.
 */
final class PreyCells {

    /**
     * Cell width in blocks. Larger than the zombie grid's 8 on purpose: target queries use
     * {@code targetDetectRadius} (40 by default) rather than a melee-scale radius, and the probe count
     * grows as {@code ((2*radius/cell)+1)²}, so 121 cells at 8 blocks against 36 at 16. Prey are sparse
     * enough that the coarser bucket costs nothing on the filtering side.
     */
    static final int CELL = 16;

    private final Long2ObjectMap<List<LivingEntity>> cells = new Long2ObjectOpenHashMap<>();
    private final Int2LongMap cellOf = new Int2LongOpenHashMap();

    PreyCells() {
        cellOf.defaultReturnValue(Long.MIN_VALUE);
    }

    static long keyOf(double x, double z) {
        return CellMath.packKey(Math.floorDiv((int) Math.floor(x), CELL),
                                Math.floorDiv((int) Math.floor(z), CELL));
    }

    List<LivingEntity> at(int cx, int cz) {
        return cells.get(CellMath.packKey(cx, cz));
    }

    /** Put an entity in the cell its position names, remembering which one that was. */
    void insert(LivingEntity e) {
        long k = keyOf(e.getX(), e.getZ());
        cells.computeIfAbsent(k, key -> new ArrayList<>(4)).add(e);
        cellOf.put(e.getId(), k);
    }

    /** Drop an entity from whichever cell it was last filed under. */
    void remove(LivingEntity e) {
        long k = cellOf.remove(e.getId());
        if (k != Long.MIN_VALUE) {
            removeFrom(k, e);
        }
    }

    /** Re-file an entity that moved. A no-op while it stays inside the same cell, the common case. */
    void reposition(LivingEntity e) {
        long now = keyOf(e.getX(), e.getZ());
        long was = cellOf.get(e.getId());
        if (was == now) {
            return;
        }
        if (was != Long.MIN_VALUE) {
            removeFrom(was, e);
        }
        cells.computeIfAbsent(now, key -> new ArrayList<>(4)).add(e);
        cellOf.put(e.getId(), now);
    }

    void clear() {
        cells.clear();
        cellOf.clear();
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
}
