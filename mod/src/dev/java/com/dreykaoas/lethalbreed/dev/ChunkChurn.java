package com.dreykaoas.lethalbreed.dev;

import com.dreykaoas.lethalbreed.LethalBreed;

import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

/**
 * Binds {@link ChunkCycles} to a real arena: three lines of glue over a state machine that is unit-tested on
 * its own. Everything that could be got wrong — when a round trip counts, what happens when the chunk never
 * drops, whether the force-load is restored — lives in the tested half; this class only turns an {@code Action}
 * into a {@code setChunkForced} call and answers "is the witness resident" by re-finding it.
 *
 * <p><b>Re-found by UUID every tick, never cached.</b> A reload deserialises a NEW entity object; a held
 * reference points at a removed husk that keeps answering with its pre-unload state, so a cached witness
 * reports "still here" forever and no cycle ever advances.
 */
public final class ChunkChurn {

    private final ChunkCycles cycles;
    private final String label;
    private final int cx;
    private final int cz;
    private boolean reported;

    /**
     * @param label        names this churn in the log line emitted when it finishes
     * @param timeoutTicks per-half budget; size it from the SLOWEST observed chunk drop on the target
     *                     hardware. See {@link ChunkCycles} for why the typical case is the wrong yardstick.
     */
    public ChunkChurn(String label, int cx, int cz, int rounds, int timeoutTicks) {
        this.label = label;
        this.cx = cx;
        this.cz = cz;
        this.cycles = new ChunkCycles(rounds, timeoutTicks);
    }

    /** Call once per tick while the churn should run. Cheap and idempotent once finished. */
    public void drive(ServerLevel ow, int tick, UUID witness) {
        if (cycles.finished()) {
            return;
        }
        // The RUNTIME id, not merely "is it there": a deserialised entity is a new object with a new id, so a
        // changed id is proof a reload happened where mere presence is only evidence.
        net.minecraft.world.entity.Entity e = witness == null ? null : ow.getEntity(witness);
        int id = e == null ? ChunkCycles.ABSENT : e.getId();
        switch (cycles.step(tick, id)) {
            case RELEASE -> ArenaBuilder.releaseChunks(ow, cx, cz);
            case REFORCE -> ArenaBuilder.forceChunks(ow, cx, cz);
            case NONE -> { }
        }
        if (cycles.finished() && !reported) {
            reported = true;
            LethalBreed.LOGGER.info("[LB-Churn] {} @({},{}) finished at t={}: {}",
                    label, cx, cz, tick, cycles.diagnosis());
        }
    }

    /** Round trips genuinely observed. A verdict about reload behaviour must FAIL when this is zero. */
    public int completed() {
        return cycles.completed();
    }

    public boolean finished() {
        return cycles.finished();
    }

    public String diagnosis() {
        return cycles.diagnosis();
    }
}
