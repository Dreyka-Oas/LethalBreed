package oas.dreyka.lethalbreed.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-dimension persistence for the dirt zombies leave behind. Lives in
 * {@code <world>/data/lethalbreed_placed.dat}.
 *
 * <p>Without this the tracker was a plain map rebuilt empty at every boot, so a world closed and reopened
 * kept every placed block forever: nothing was tracked, so nothing ever crumbled. Bridging and pillaring
 * reshaped the world permanently for anyone who saves and comes back, which is the one thing
 * {@link PlacedBlockTracker} exists to prevent.
 *
 * <p>The placement time is stored as the world age ({@code ServerLevel.getGameTime()}), which is itself
 * persisted and monotonic across reloads. A since-boot counter would restart at 0 every launch and make
 * every restored placement look as if it had been laid in the future.
 */
public final class PlacedBlockSavedData extends SavedData {

    private final List<PlacedBlockTracker.Placement> placements = new ArrayList<>();

    private static final Codec<PlacedBlockTracker.Placement> PLACEMENT =
            RecordCodecBuilder.create(i -> i.group(
                    Codec.LONG.fieldOf("pos").forGetter(PlacedBlockTracker.Placement::packedPos),
                    Codec.LONG.fieldOf("at").forGetter(PlacedBlockTracker.Placement::placedAt)
            ).apply(i, PlacedBlockTracker.Placement::new));

    public static final Codec<PlacedBlockSavedData> CODEC = RecordCodecBuilder.create(i -> i.group(
            PLACEMENT.listOf().fieldOf("placed").forGetter(PlacedBlockSavedData::placements)
    ).apply(i, PlacedBlockSavedData::new));

    public static final SavedDataType<PlacedBlockSavedData> TYPE =
            new SavedDataType<>("lethalbreed_placed", PlacedBlockSavedData::new, CODEC, DataFixTypes.LEVEL);

    /** Fresh world: nothing placed yet. */
    public PlacedBlockSavedData() {
        this(List.of());
    }

    public PlacedBlockSavedData(List<PlacedBlockTracker.Placement> placed) {
        placements.addAll(placed);
    }

    public List<PlacedBlockTracker.Placement> placements() {
        return placements;
    }

    /** Replace the stored snapshot with what the tracker currently holds. */
    public void store(List<PlacedBlockTracker.Placement> live) {
        placements.clear();
        placements.addAll(live);
        setDirty();
    }
}
