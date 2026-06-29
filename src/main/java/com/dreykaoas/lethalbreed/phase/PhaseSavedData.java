package com.dreykaoas.lethalbreed.phase;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Per-world persistence for the difficulty phase. Lives in {@code <world>/data/lethalbreed_phase.dat} and is
 * auto-saved with the world (on autosave and on server stop), so the escalation survives close/reopen instead
 * of resetting to phase 1 each session.
 *
 * <p>The timer basis is the overworld's {@code getGameTime()} (the persisted world age), so the elapsed time
 * toward the next advance is preserved across reloads too — not just the phase number.
 */
public final class PhaseSavedData extends SavedData {

    int phase;
    long lastAdvanceGameTime;
    long nextIntervalTicks;

    public static final Codec<PhaseSavedData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("phase").forGetter(d -> d.phase),
            Codec.LONG.fieldOf("lastAdvanceGameTime").forGetter(d -> d.lastAdvanceGameTime),
            Codec.LONG.fieldOf("nextIntervalTicks").forGetter(d -> d.nextIntervalTicks)
    ).apply(i, PhaseSavedData::new));

    public static final SavedDataType<PhaseSavedData> TYPE =
            new SavedDataType<>("lethalbreed_phase", PhaseSavedData::new, CODEC, DataFixTypes.LEVEL);

    /** Fresh-world defaults: phase 1, timer un-started (first tick seeds it from the current world age). */
    public PhaseSavedData() {
        this(1, Long.MIN_VALUE, -1L);
    }

    public PhaseSavedData(int phase, long lastAdvanceGameTime, long nextIntervalTicks) {
        this.phase = phase;
        this.lastAdvanceGameTime = lastAdvanceGameTime;
        this.nextIntervalTicks = nextIntervalTicks;
    }
}
