package com.dreykaoas.lethalbreed.dev.arena;

import com.dreykaoas.lethalbreed.dev.DevVerdict;

import com.dreykaoas.lethalbreed.GameState;
import com.dreykaoas.lethalbreed.LethalBreed;
import com.dreykaoas.lethalbreed.block.PlacedBlockPolicy;
import com.dreykaoas.lethalbreed.block.PlacedBlockTracker;
import com.dreykaoas.lethalbreed.config.domain.CombatMoveConfig;
import com.dreykaoas.lethalbreed.config.domain.DevTestConfig;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * The lifetime contract of {@link PlacedBlockTracker}, driven directly rather than through a zombie.
 *
 * <p><b>Why not make zombies bridge.</b> That is {@code BreachHarness}'s job, and a far noisier experiment:
 * which block a zombie chooses, when, and how many all depend on the flow field, the breach coordinator and
 * the stuck detector. None of that is the tracker's contract. Here the tracker's own {@code record()} is
 * called through the real {@code WorldAIContext.placedBlocks()} accessor — the same instance
 * {@code WorldMaintenance.drainBlockOps} ticks every server tick — so everything after that call is shipped
 * code, unmocked. The arithmetic (expiry, abandon threshold, crack ramp) is already unit-tested in
 * {@code PlacedBlockPolicyTest}; what CANNOT be unit-tested, and is the entire point of this rig, is the
 * {@code level.isLoaded} branch, with a real {@code ServerLevel} and real chunk residency.
 *
 * <p>Three behaviours, all on one timeline:
 * <ul>
 *   <li><b>loaded</b> — placements in a force-loaded chunk crumble and pop at their lifetime, and pop with NO
 *       item drop ({@code destroyBlock(pos, false, …)}); a raid must not carpet the ground in dirt items.</li>
 *   <li><b>unloaded → HELD</b> — a placement whose chunk is not resident is neither read nor destroyed nor
 *       forgotten, however far past its lifetime it is. Forgetting it is the failure that matters: the
 *       tracker could then never come back and remove the block, so a zombie's dirt bridge over a ravine
 *       would stand as permanent terrain — precisely what the tracker exists to prevent. Held, it is resolved
 *       the moment the chunk becomes readable again.</li>
 *   <li><b>unloaded forever → ABANDONED</b> — that retention still has to be bounded, or an area a player
 *       explores once and never revisits grows the map without limit. At
 *       {@link PlacedBlockPolicy#ABANDON_FACTOR} lifetimes the tracker gives up.</li>
 * </ul>
 *
 * <h2>Why "unloaded" is produced by never loading a chunk, not by unloading one</h2>
 *
 * <p>The obvious rig — force-load a chunk, place in it, release it, wait — was built first and does not work
 * headless. Two measured reasons:
 *
 * <ol>
 *   <li>A {@code FORCED} ticket does not pin only its own chunk. The ticket level propagates outward one per
 *       chunk and everything at or below {@code ChunkLevel.MAX_LEVEL} (33) is still loaded, so a chunk two
 *       away from a force-loaded one never unloads at all: with the released floor at chunk (1,31) and the
 *       arena at (1,28..29), {@code isLoaded} stayed true for 1000+ ticks.</li>
 *   <li>Even six chunks clear, the latency between {@code setChunkForced(false)} and {@code isLoaded} going
 *       false is not a constant. Across four runs of this same rig it was 2, ~35, 272 ticks, and once it had
 *       still not happened after 1200. Any schedule built on it either races the expiry — the placement dies
 *       through the ordinary loaded path and the rig reports a FAIL that describes its own timing — or hangs.
 *       A verification rig must not have a race in it.</li>
 * </ol>
 *
 * <p>So the unloaded state is obtained the one way that is deterministic: positions in chunks nobody has ever
 * touched. {@code record()} performs no world access — it is a map insert keyed on the packed position — so
 * recording a placement in a virgin chunk puts the tracker in exactly the state a real bridge reaches once
 * the player walks away, and {@code isLoaded} is false by construction rather than by luck. The ages are
 * back-dated for the same reason: {@code age = now − placedAt} is the tracker's own arithmetic, so handing it
 * a past {@code placedAt} is indistinguishable from waiting, and it keeps
 * {@code ABANDON_FACTOR × lifetime} out of the run's wall-clock. Loading a chunk on demand IS reliable, so
 * the "resolved once the chunk comes back" half is still driven by a genuine chunk load.
 */
public final class PlacedBlockHarness {
    private PlacedBlockHarness() {}

    private static final String SUITE = "placed";

    private static final int Y = ArenaBuilder.VERIFY_Y;  // 101 — the placements
    private static final int FLOOR_Y = Y - 1;            // 100 — stone under them

    /** Floor A: inside the 3×3 force-load taken by {@link ArenaBuilder#forceChunks} — loaded throughout. */
    private static final int AX = 30;
    private static final int AZ = ArenaBuilder.VERIFY_BAND_Z + 60; // z=460
    /** The HELD placement: a chunk nothing has ever loaded, force-loaded near the end so it resolves. */
    private static final BlockPos HELD_POS = new BlockPos(30, Y, 2000);
    /** The ABANDONED placement: a chunk nothing ever loads. */
    private static final BlockPos LOST_POS = new BlockPos(30, Y, 3000);

    /** Rig lifetime override; the shipped 600 is restored when the suite closes. */
    private static final int LIFETIME = 60;
    private static final long ABANDON_AGE = LIFETIME * PlacedBlockPolicy.ABANDON_FACTOR; // 600

    /** Age {@link #HELD_POS} carries at {@link #PLACE_TICK}: far past {@link #LIFETIME}, far short of abandon. */
    private static final int HELD_BACKDATE = 200;
    /** Age {@link #LOST_POS} carries at {@link #PLACE_TICK}: 80 ticks short of the abandon threshold, so the
     *  rig observes it both HELD (t={@link #EXPIRE_TICK}) and given up (t={@link #ABANDON_TICK}). */
    private static final long LOST_BACKDATE = ABANDON_AGE - 80;

    /** Ticks a chunk load may take before the rig FAILs rather than hanging the run. */
    private static final int WAIT_TIMEOUT = 600;

    // ---- Schedule -------------------------------------------------------------------------------------
    private static final int FORCE_TICK = 1;
    private static final int PLACE_TICK = 10;
    private static final int PRESENT_TICK = 30;
    /** A's age here is 70 > 60; HELD is at 290; LOST is at 590 — over age, 10 short of being abandoned. */
    private static final int EXPIRE_TICK = 80;
    /** LOST crosses ABANDON_AGE at t=90. */
    private static final int ABANDON_TICK = 110;
    private static final int LOAD_HELD_TICK = 120;

    private enum Step { FORCE, PLACE, PRESENT, EXPIRE, ABANDON, LOAD_HELD, WAIT_HELD_LOADED, RESOLVE, DONE }

    private static int tick = -1;
    private static Step step = Step.FORCE;
    private static int stepAt = FORCE_TICK;
    private static int waitStart = 0;
    private static long savedLifetime;

    private static final List<BlockPos> A_POS = new ArrayList<>();
    private static int placedAt = 0;

    public static void onTick(MinecraftServer server) {
        if (!DevTestConfig.devPlacedTest || !FabricLoader.getInstance().isDevelopmentEnvironment()
                || step == Step.DONE) {
            return;
        }
        tick++;
        if (tick < stepAt) {
            return;
        }
        ServerLevel ow = server.overworld();
        switch (step) {
            case FORCE -> {
                force(ow);
                at(Step.PLACE, PLACE_TICK);
            }
            case PLACE -> {
                place(ow);
                at(Step.PRESENT, PRESENT_TICK);
            }
            case PRESENT -> {
                checkPresent(ow);
                at(Step.EXPIRE, EXPIRE_TICK);
            }
            case EXPIRE -> {
                checkExpired(ow);
                at(Step.ABANDON, ABANDON_TICK);
            }
            case ABANDON -> {
                checkAbandoned(ow);
                at(Step.LOAD_HELD, LOAD_HELD_TICK);
            }
            case LOAD_HELD -> {
                setForced(ow, HELD_POS, true);
                LethalBreed.LOGGER.info("[Placed] force-loading the HELD placement's chunk ({}, {}).",
                        HELD_POS.getX() >> 4, HELD_POS.getZ() >> 4);
                step = Step.WAIT_HELD_LOADED;
                waitStart = tick;
                stepAt = tick + 1;
            }
            case WAIT_HELD_LOADED -> {
                if (ow.isLoaded(HELD_POS)) {
                    LethalBreed.LOGGER.info("[Placed] HELD chunk resident after {} ticks.", tick - waitStart);
                    step = Step.RESOLVE;
                    stepAt = tick + 20; // let the tracker's next ticks act on it
                } else if (tick - waitStart >= WAIT_TIMEOUT) {
                    DevVerdict.check(SUITE, "resolved-on-reload", false,
                            "the held placement's chunk never became resident within " + WAIT_TIMEOUT
                                    + " ticks — harness failure, not a silent pass");
                    finish(ow, server);
                }
            }
            case RESOLVE -> {
                checkResolved(ow);
                finish(ow, server);
            }
            default -> { }
        }
    }

    private static void at(Step next, int when) {
        step = next;
        stepAt = when;
    }

    private static PlacedBlockTracker tracker(ServerLevel ow) {
        return GameState.DIMENSIONS.get(ow.dimension()).placedBlocks();
    }

    private static void setForced(ServerLevel ow, BlockPos p, boolean forced) {
        ow.setChunkForced(p.getX() >> 4, p.getZ() >> 4, forced);
    }

    private static void force(ServerLevel ow) {
        savedLifetime = CombatMoveConfig.placedBlockLifetimeTicks;
        CombatMoveConfig.placedBlockLifetimeTicks = LIFETIME;
        ArenaBuilder.forceChunks(ow, AX, AZ);
        LethalBreed.LOGGER.info("[Placed] lifetime {} -> {} ticks for this run; abandon at {} ticks of age.",
                savedLifetime, LIFETIME, ABANDON_AGE);
    }

    private static void place(ServerLevel ow) {
        for (int x = AX - 2; x <= AX + 2; x++) {
            for (int z = AZ - 2; z <= AZ + 2; z++) {
                ow.setBlock(new BlockPos(x, FLOOR_Y, z), Blocks.STONE.defaultBlockState(), 3);
                for (int dy = 0; dy <= 3; dy++) {
                    ow.setBlock(new BlockPos(x, Y + dy, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        A_POS.clear();
        A_POS.add(new BlockPos(AX - 1, Y, AZ));
        A_POS.add(new BlockPos(AX, Y, AZ));
        A_POS.add(new BlockPos(AX + 1, Y, AZ));
        PlacedBlockTracker t = tracker(ow);
        placedAt = tick;
        for (BlockPos p : A_POS) {
            ow.setBlock(p, Blocks.DIRT.defaultBlockState(), 3);
            t.record(p, tick);
        }
        // The two unloaded cases. Asserted rather than assumed: if either chunk were somehow resident, both
        // would be exercising the loaded branch and every "held" claim below would be about nothing.
        boolean heldUnloaded = !ow.isLoaded(HELD_POS);
        boolean lostUnloaded = !ow.isLoaded(LOST_POS);
        DevVerdict.check(SUITE, "unloaded-precondition", heldUnloaded && lostUnloaded,
                "chunk residency at record time — held@" + HELD_POS + " loaded=" + !heldUnloaded
                        + ", lost@" + LOST_POS + " loaded=" + !lostUnloaded);
        t.record(HELD_POS, tick - HELD_BACKDATE);
        t.record(LOST_POS, tick - LOST_BACKDATE);
        LethalBreed.LOGGER.info("[Placed] recorded 5 placements at t={}: 3 loaded, 1 unloaded at age {}, "
                        + "1 unloaded at age {}; tracked={}",
                tick, HELD_BACKDATE, LOST_BACKDATE, t.trackedCount());
    }

    private static void checkPresent(ServerLevel ow) {
        int dirt = 0;
        for (BlockPos p : A_POS) {
            if (ow.getBlockState(p).is(Blocks.DIRT)) {
                dirt++;
            }
        }
        int tracked = tracker(ow).trackedCount();
        DevVerdict.check(SUITE, "still-present", dirt == 3 && tracked == 5,
                "loaded placements at age " + (tick - placedAt) + "/" + LIFETIME + ": " + dirt
                        + "/3 still DIRT; trackedCount=" + tracked + " (3 loaded + 2 unloaded)");
    }

    private static void checkExpired(ServerLevel ow) {
        int air = 0;
        for (BlockPos p : A_POS) {
            if (ow.getBlockState(p).isAir()) {
                air++;
            }
        }
        DevVerdict.check(SUITE, "expired-popped", air == 3,
                "age=" + (tick - placedAt) + " > lifetime=" + LIFETIME + ": " + air
                        + "/3 loaded placements are air");

        AABB box = new AABB(AX - 1, Y - 1, AZ, AX + 2, Y + 2, AZ + 1).inflate(4.0);
        List<ItemEntity> drops = ow.getEntitiesOfClass(ItemEntity.class, box);
        DevVerdict.check(SUITE, "no-drop", drops.isEmpty(),
                drops.size() + " ItemEntity in " + box + " — destroyBlock is called with dropBlock=false");

        int tracked = tracker(ow).trackedCount();
        long heldAge = tick - placedAt + HELD_BACKDATE;
        long lostAge = tick - placedAt + LOST_BACKDATE;
        DevVerdict.check(SUITE, "unloaded-retained",
                tracked == 2 && !ow.isLoaded(HELD_POS) && !ow.isLoaded(LOST_POS),
                "the two unloaded entries are " + heldAge + " and " + lostAge + " ticks old — both far past "
                        + "lifetime=" + LIFETIME + ", neither past abandon=" + ABANDON_AGE + " — and both are "
                        + "still tracked: trackedCount=" + tracked + " (dropping them would leave their blocks "
                        + "in the world forever)");
    }

    private static void checkAbandoned(ServerLevel ow) {
        int tracked = tracker(ow).trackedCount();
        long heldAge = tick - placedAt + HELD_BACKDATE;
        long lostAge = tick - placedAt + LOST_BACKDATE;
        DevVerdict.check(SUITE, "abandon-factor", tracked == 1 && lostAge >= ABANDON_AGE
                        && heldAge < ABANDON_AGE,
                "unloaded ages now " + heldAge + " (< " + ABANDON_AGE + ", kept) and " + lostAge + " (>= "
                        + ABANDON_AGE + ", given up): trackedCount=" + tracked
                        + " — retention is bounded, and bounded only at ABANDON_FACTOR lifetimes");
    }

    private static void checkResolved(ServerLevel ow) {
        boolean loaded = ow.isLoaded(HELD_POS);
        boolean air = loaded && ow.getBlockState(HELD_POS).isAir();
        int tracked = tracker(ow).trackedCount();
        DevVerdict.check(SUITE, "resolved-on-reload", loaded && air && tracked == 0,
                "held entry's chunk is resident again: block air=" + air + ", trackedCount=" + tracked
                        + " — the entry the tracker refused to forget was acted on the moment it could be");
    }

    private static void finish(ServerLevel ow, MinecraftServer server) {
        CombatMoveConfig.placedBlockLifetimeTicks = savedLifetime;
        ArenaBuilder.releaseChunks(ow, AX, AZ);
        setForced(ow, HELD_POS, false);
        step = Step.DONE;
        DevVerdict.summary(SUITE, server);
    }
}
