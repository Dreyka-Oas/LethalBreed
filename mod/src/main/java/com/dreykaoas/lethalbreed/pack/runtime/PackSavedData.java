package com.dreykaoas.lethalbreed.pack.runtime;

import com.dreykaoas.lethalbreed.pack.PackState;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import com.dreykaoas.lethalbreed.dimension.DimensionManager;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Per-dimension persistence for packs, in {@code <world>/data/lethalbreed_packs.dat}.
 *
 * <p><b>Per dimension, not overworld-global.</b> {@code PhaseSavedData} — the only precedent in this repo —
 * is global because a difficulty phase is. Packs are not: a {@code PackManager} lives on
 * {@code WorldAIContext}, one per dimension, and a pack never crosses into another. Storing them globally
 * would merge the Nether's packs into the overworld's on the next load.
 *
 * <p>Live members are deliberately <b>not</b> written. They are ordinary entities, saved with their chunk
 * like everything else, and they carry their pack id in a persistent attachment — so they re-join on the way
 * back rather than being restored from here. What must be written is what nothing else owns: the pack's
 * route, its random seed, its dematerialised ghosts, and how many members went to disk before we could
 * snapshot them. Dropping that last count would strand every returning member as an orphan, because a pack
 * with no live and no ghost members reads as empty and is dissolved on load.
 */
public final class PackSavedData extends SavedData {

    private static final Codec<PackState.Ghost> GHOST = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("msb").forGetter(PackState.Ghost::uuidMsb),
            Codec.LONG.fieldOf("lsb").forGetter(PackState.Ghost::uuidLsb),
            // Base64 rather than a raw byte array: the NBT blob is already gzipped, and a string field
            // survives a hand-inspection of the .dat without a special reader.
            Codec.STRING.fieldOf("nbt").forGetter(g -> Base64.getEncoder().encodeToString(g.nbt())),
            // Optional so a .dat written before this field existed loads as retries=0, not a parse failure.
            Codec.INT.optionalFieldOf("retries", 0).forGetter(PackState.Ghost::retries)
    ).apply(i, (msb, lsb, b64, retries) -> new PackState.Ghost(msb, lsb, Base64.getDecoder().decode(b64), retries)));

    private static final Codec<PackState> PACK = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("id").forGetter(p -> p.id),
            Codec.DOUBLE.fieldOf("x").forGetter(p -> p.x),
            Codec.DOUBLE.fieldOf("z").forGetter(p -> p.z),
            Codec.INT.fieldOf("destX").forGetter(p -> p.destX),
            Codec.INT.fieldOf("destZ").forGetter(p -> p.destZ),
            Codec.DOUBLE.fieldOf("headingX").forGetter(p -> p.headingX),
            Codec.DOUBLE.fieldOf("headingZ").forGetter(p -> p.headingZ),
            Codec.LONG.fieldOf("seed").forGetter(p -> p.seed),
            Codec.BOOL.fieldOf("virtual").forGetter(p -> p.phase == PackState.Phase.VIRTUAL),
            Codec.LONG.fieldOf("dwellUntil").forGetter(p -> p.dwellUntil),
            Codec.INT.fieldOf("detached").forGetter(p -> p.detached),
            GHOST.listOf().fieldOf("ghosts").forGetter(p -> p.ghosts)
    ).apply(i, PackSavedData::rebuild));

    public static final Codec<PackSavedData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("nextId").forGetter(d -> d.nextId),
            PACK.listOf().fieldOf("packs").forGetter(d -> d.packs)
    ).apply(i, PackSavedData::new));

    public static final SavedDataType<PackSavedData> TYPE =
            new SavedDataType<>("lethalbreed_packs", PackSavedData::new, CODEC, DataFixTypes.LEVEL);

    private long nextId;
    private final List<PackState> packs;

    public PackSavedData() {
        this(1L, List.of());
    }

    public PackSavedData(long nextId, List<PackState> packs) {
        this.nextId = nextId;
        this.packs = new ArrayList<>(packs);
    }

    private static PackState rebuild(long id, double x, double z, int destX, int destZ,
                                     double headingX, double headingZ, long seed, boolean virtual,
                                     long dwellUntil, int detached, List<PackState.Ghost> ghosts) {
        PackState p = new PackState(id, x, z, seed);
        p.destX = destX;
        p.destZ = destZ;
        p.headingX = headingX;
        p.headingZ = headingZ;
        p.phase = virtual ? PackState.Phase.VIRTUAL : PackState.Phase.MATERIAL;
        p.dwellUntil = dwellUntil;
        p.detached = detached;
        p.ghosts.addAll(ghosts);
        return p;
    }

    public long nextId() {
        return nextId;
    }

    public List<PackState> packs() {
        return packs;
    }

    /** Hand every dimension's saved packs to its own manager. Called once, at SERVER_STARTED. */
    public static void loadAll(MinecraftServer server, DimensionManager dimensions) {
        for (ServerLevel level : server.getAllLevels()) {
            PackSavedData data = level.getDataStorage().computeIfAbsent(TYPE);
            dimensions.get(level.dimension()).packManager().restore(data.packs(), data.nextId());
        }
    }

    /**
     * Write every dimension's packs back.
     *
     * <p>Must run on SERVER_STOPPING, not STOPPED: {@code stopServer()} saves the chunks and the data
     * storage between the two events, so this is the last moment a write still reaches the disk.
     */
    public static void saveAll(MinecraftServer server, DimensionManager dimensions) {
        for (ServerLevel level : server.getAllLevels()) {
            var manager = dimensions.get(level.dimension()).packManager();
            level.getDataStorage().computeIfAbsent(TYPE).store(manager.nextId(), manager.all());
        }
    }

    /** Replace the stored snapshot with what the manager currently holds. */
    public void store(long nextId, Iterable<PackState> live) {
        this.nextId = nextId;
        packs.clear();
        for (PackState p : live) {
            packs.add(p);
        }
        setDirty();
    }
}
