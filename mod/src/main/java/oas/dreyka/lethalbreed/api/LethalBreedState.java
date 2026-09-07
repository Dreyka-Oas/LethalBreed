package oas.dreyka.lethalbreed.api;

import oas.dreyka.lethalbreed.GameState;
import oas.dreyka.lethalbreed.api.variant.SpecialVariant;
import oas.dreyka.lethalbreed.api.variant.SpecialVariantRegistry;
import oas.dreyka.lethalbreed.dimension.WorldAiContext;
import oas.dreyka.lethalbreed.pack.PackState;
import oas.dreyka.lethalbreed.phase.PhaseManager;
import oas.dreyka.lethalbreed.special.SpecialAttachment;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.zombie.Zombie;

import java.util.ArrayList;
import java.util.List;

/**
 * What the mod currently believes, read-only. Nothing here changes anything, and nothing here needs the addon
 * to have registered for a callback first: this is the half of the surface that answers a question asked at an
 * arbitrary moment, where {@link LethalBreedApi} is the half that waits to be told.
 *
 * <p>Everything crossing the boundary is a vanilla type, a primitive, or {@link Pack}, which is declared here
 * rather than being the mod's own {@code PackState} handed out. An addon compiled against this file keeps
 * compiling when a field is added to that class, renamed, or moved onto something else.
 *
 * <p><b>Server thread only.</b> The registry and the per-dimension contexts are written from the tick loop
 * without a lock, exactly as the rest of the mod reads them. Called from the phase listener, from a command,
 * or from anything else on that thread, these answers are the ones the mod is acting on.
 */
public final class LethalBreedState {
    private LethalBreedState() {}

    /** The night progression as it stands. Zero on a fresh world; there is no upper bound. */
    public static int phase() {
        return PhaseManager.current();
    }

    /**
     * How many zombies the mod is driving right now, every dimension counted together.
     *
     * <p>This is the population its own budgets are computed against, not {@code level.getEntities()}: a
     * zombie the mod declined to take over is not in it, and one whose chunk unloaded has already left.
     */
    public static int trackedZombieCount() {
        return GameState.REGISTRY.size();
    }

    /**
     * The special variant this zombie carries, or null for an ordinary one.
     *
     * <p>A variant nobody registered this session reads as null even though the save still names it, which is
     * what happens to an addon's variants when its jar is pulled. The id stays on the entity either way, so
     * putting the jar back brings the variant back rather than leaving a stripped zombie behind.
     */
    public static SpecialVariant variantOf(Zombie zombie) {
        return zombie == null ? null : SpecialVariantRegistry.byId(zombie.getAttached(SpecialAttachment.SPECIAL));
    }

    /**
     * Every pack in one dimension, as a snapshot taken now.
     *
     * <p>The list is built rather than being a view, so iterating it while the sweep dissolves a pack is safe.
     * A dimension the mod has never seen a zombie in answers with an empty list instead of being given a
     * context it has no use for.
     */
    public static List<Pack> packs(ServerLevel level) {
        List<Pack> out = new ArrayList<>();
        if (level == null) {
            return out;
        }
        WorldAiContext ctx = GameState.DIMENSIONS.contexts().get(level.dimension());
        if (ctx == null) {
            return out;
        }
        for (PackState p : ctx.packManager().all()) {
            out.add(new Pack(p.id, p.x, p.z, p.destX, p.destZ, p.totalMembers(), p.liveIds.size()));
        }
        return out;
    }

    /**
     * One pack, flattened.
     *
     * @param id stable for the pack's whole life and reused by nothing after it dissolves
     * @param x centre, recomputed from the members at every visit rather than every tick
     * @param z centre, same
     * @param destX where it is walking to, block coordinates
     * @param destZ where it is walking to, block coordinates
     * @param members everyone it counts as its own: loaded, snapshotted, and gone to disk with a chunk
     * @param loaded the subset of those that exist as entities at this instant
     */
    public record Pack(long id, double x, double z, int destX, int destZ, int members, int loaded) { }
}
