package oas.dreyka.lethalbreed.api.variant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Every special variant the game knows about, the mod's eight included.
 *
 * <p>The eight come in through this same door, from {@code ShippedVariants}, so an addon's variant is not a
 * second-class citizen bolted to the side: the roll, the save and the tick all read the registry and cannot
 * tell whose variant they are looking at.
 *
 * <p>Register from {@code LethalBreedAddon.onLethalBreedReady()}. Registering is one way; there is no
 * unregister, because a variant that vanished while a world holds zombies carrying its id would turn those
 * zombies into a save entry that resolves to nothing.
 */
public final class SpecialVariantRegistry {
    private SpecialVariantRegistry() {}

    // Two structures on purpose: the map answers a save id in constant time on the entity-load path, the
    // list keeps registration order so a roll is reproducible across restarts. Both concurrent because
    // registration is mod-init and every read after that is the server thread.
    private static final Map<String, SpecialVariant> BY_ID = new ConcurrentHashMap<>();
    private static final List<SpecialVariant> ORDER = new CopyOnWriteArrayList<>();
    private static final Map<String, String> ALIASES = new ConcurrentHashMap<>();

    /**
     * Add a variant.
     *
     * @return the variant given, so a caller can keep the reference in the same statement
     * @throws IllegalArgumentException if the id is taken, whether by a variant or by an alias. Two mods
     *         sharing an id would each read the other's zombies as their own, which is why this is a
     *         refusal and not a warning
     */
    public static SpecialVariant register(SpecialVariant variant) {
        String id = variant.id();
        if (ALIASES.containsKey(id)) {
            throw new IllegalArgumentException("variant id " + id + " is already an alias for "
                    + ALIASES.get(id));
        }
        if (BY_ID.putIfAbsent(id, variant) != null) {
            throw new IllegalArgumentException("variant id " + id + " is already registered");
        }
        ORDER.add(variant);
        return variant;
    }

    /**
     * Make an id that is no longer written resolve to a variant that is, for worlds saved before a rename.
     *
     * <p>One way only, like {@link #register}: {@link #byId} accepts the old id, nothing ever writes it
     * back, so a world re-saves itself onto the current id as its chunks cycle.
     */
    public static void alias(String oldId, String id) {
        if (BY_ID.containsKey(oldId)) {
            throw new IllegalArgumentException("cannot alias " + oldId + ", a variant already holds it");
        }
        ALIASES.put(oldId, id);
    }

    /** The variant a save id names, or null when nothing answers to it. */
    public static SpecialVariant byId(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        SpecialVariant v = BY_ID.get(id);
        return v != null ? v : BY_ID.get(ALIASES.getOrDefault(id, ""));
    }

    /** Every variant, in registration order. */
    public static List<SpecialVariant> all() {
        return Collections.unmodifiableList(ORDER);
    }

    /** Those a zombie may roll at this phase. */
    public static List<SpecialVariant> available(int phase) {
        List<SpecialVariant> out = new ArrayList<>();
        for (SpecialVariant v : ORDER) {
            if (phase >= v.unlockPhase().getAsInt()) {
                out.add(v);
            }
        }
        return out;
    }

    /**
     * The highest phase any variant waits for, so a server with the phase system switched off can unlock
     * everything at once instead of keeping content behind a progression that never advances.
     */
    public static int maxUnlockPhase() {
        int max = 0;
        for (SpecialVariant v : ORDER) {
            max = Math.max(max, v.unlockPhase().getAsInt());
        }
        return max;
    }

    /** Undo a registration. Tests only: they share one JVM and one registry, and a variant left behind
     *  would join another test's roll. Nothing in production forgets a variant, see the class javadoc. */
    static void forget(String id) {
        SpecialVariant v = BY_ID.remove(id);
        if (v != null) {
            ORDER.remove(v);
        }
        ALIASES.values().removeIf(id::equals);
        ALIASES.remove(id);
    }
}
