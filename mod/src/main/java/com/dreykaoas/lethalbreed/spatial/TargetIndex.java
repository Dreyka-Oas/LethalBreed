package com.dreykaoas.lethalbreed.spatial;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Spatial index of PREY, every living entity a zombie may hunt, one per dimension. Players are
 * deliberately NOT held here; they are read live from the level at lookup time. See the package
 * documentation for why both of those are so.
 */
public final class TargetIndex {

    private final PreyCells cells = new PreyCells();
    private final Int2ObjectMap<LivingEntity> tracked = new Int2ObjectOpenHashMap<>();
    private final List<LivingEntity> stale = new ArrayList<>();

    /** True for entities this index is responsible for. Zombies are the horde itself, players are queried
     *  live — everything else living is prey and gets indexed. */
    public static boolean indexable(net.minecraft.world.entity.Entity e) {
        return e instanceof LivingEntity && !(e instanceof Zombie) && !(e instanceof Player);
    }

    /** Start tracking a prey entity. Idempotent: a re-load of the same id just re-buckets it. */
    public void track(LivingEntity e) {
        if (tracked.containsKey(e.getId())) {
            cells.reposition(e);
            return;
        }
        tracked.put(e.getId(), e);
        cells.insert(e);
    }

    /** Stop tracking, by id so it works from an unload callback that only has the entity. */
    public void forget(int entityId) {
        LivingEntity e = tracked.remove(entityId);
        if (e != null) {
            cells.remove(e);
        }
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
                cells.reposition(e);
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
        int minCx = CellMath.floorCell(x - radius, PreyCells.CELL);
        int maxCx = CellMath.floorCell(x + radius, PreyCells.CELL);
        int minCz = CellMath.floorCell(z - radius, PreyCells.CELL);
        int maxCz = CellMath.floorCell(z + radius, PreyCells.CELL);
        double r2 = radius * radius;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                List<LivingEntity> list = cells.at(cx, cz);
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
        stale.clear();
    }

    public int size() {
        return tracked.size();
    }
}
