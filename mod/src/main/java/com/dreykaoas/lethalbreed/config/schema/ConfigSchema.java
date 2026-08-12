package com.dreykaoas.lethalbreed.config.schema;

import com.dreykaoas.lethalbreed.config.ConfigAccess;

import com.dreykaoas.lethalbreed.config.domain.CombatMoveConfig;
import com.dreykaoas.lethalbreed.config.domain.ContaminationConfig;
import com.dreykaoas.lethalbreed.config.domain.engine.ExpertConfig;
import com.dreykaoas.lethalbreed.config.domain.engine.FlowConfig;
import com.dreykaoas.lethalbreed.config.domain.PackConfig;
import com.dreykaoas.lethalbreed.config.domain.ProgressionConfig;
import com.dreykaoas.lethalbreed.config.domain.engine.SchedulerConfig;
import com.dreykaoas.lethalbreed.config.domain.SpecialVariantConfig;
import com.dreykaoas.lethalbreed.config.domain.TargetingConfig;
import com.dreykaoas.lethalbreed.config.domain.WorldSpawnConfig;
import com.dreykaoas.lethalbreed.config.domain.ZombieMoodConfig;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reflection enumeration of the config field set. The single source of truth for "which fields are
 * editable options" — every public, static, non-final, primitive field on each {@link #HOLDERS holder}
 * class is exposed automatically, in source-declaration order.
 *
 * <p>The field declarations live on the domain holder classes under {@code config.domain}; each is listed
 * in {@link #HOLDERS}. To add a config option, add a public static non-final primitive field to one of those
 * holders (or add a new holder to {@link #HOLDERS}) and the rest of the config layer (load/save, GUI,
 * commands) keeps working unchanged.
 *
 * <p>The holder list is not fixed at compile time: {@link #registerHolder} adds one at runtime. That exists
 * for exactly one caller — {@code DevBootstrap}, in a development environment — so the dev-only options and
 * the "Dev / Debug" GUI tab they produce simply do not exist in a player's game, and are never written into
 * a player's {@code lethalbreed.json}.
 */
public final class ConfigSchema {
    private ConfigSchema() {}

    /** Every class whose public-static-non-final primitive fields are config options. Shipped holders are
     *  listed here; {@link #registerHolder} appends a dev-only one at runtime, which is why this is a
     *  mutable list rather than the {@code Class<?>[]} it used to be. */
    private static final List<Class<?>> HOLDERS = new ArrayList<>(List.of(
            SchedulerConfig.class,
            FlowConfig.class,
            TargetingConfig.class,
            WorldSpawnConfig.class,
            CombatMoveConfig.class,
            // These two were one holder (ProgressionConfig). They MUST stay adjacent and in this order:
            // ConfigSchema.all() order is the on-disk write order within a category (ConfigSchemaOrderTest).
            ProgressionConfig.class,
            SpecialVariantConfig.class,
            ContaminationConfig.class,
            ZombieMoodConfig.class,
            ExpertConfig.class,
            // Appended last on purpose: inserting a holder mid-list would shift the on-disk write order of
            // every option after it, rewriting every existing user's config file for nothing.
            PackConfig.class));

    /** Editable fields in source-declaration order, across all holders. Cached rather than recomputed on
     *  every call (config load, every GUI keystroke via {@code ConfigAccess}, every {@code find()}) — those
     *  only ever rebuilt an identical list. Returned as an unmodifiable view since every caller only
     *  iterates it (audit #29).
     *
     *  <p>{@code volatile} and nullable rather than {@code final}: {@link #registerHolder} runs on the
     *  mod-init thread while every read happens on the server (or client) thread, so the invalidation has to
     *  publish safely. That safety does not come from "any race here is benign" — {@code all = null} is
     *  indistinguishable from the field's initial default, so a reader racing with {@link #registerHolder}
     *  would have no happens-before edge on the preceding {@code HOLDERS.add} and could cache a list
     *  omitting the just-added holder. It comes from there being no such race in practice: registration
     *  happens-before any other thread starts — {@link #registerHolder} runs as the first statement of
     *  {@code onInitialize()}, on the mod-init thread, before any server or render thread exists, and
     *  {@code Thread.start} supplies the edge. */
    private static volatile List<Field> all;

    /**
     * Add a config holder after class-init, so its options join the schema, the JSON file and the GUI.
     *
     * <p>Called only by {@code DevBootstrap} in a development environment. A shipped jar has no dev source
     * set, so this is never reached there — which is precisely why a player's config has no dev options and
     * the GUI sidebar has no "Dev / Debug" tab.
     *
     * <p>Registering the same holder twice is a no-op: the duplicate would list every one of its options
     * twice, and {@code ConfigWriter} would write each of them twice into the JSON.
     */
    public static void registerHolder(Class<?> holder) {
        if (HOLDERS.contains(holder)) {
            return;
        }
        HOLDERS.add(holder);
        all = null;
        // Without this the holder's fields have no factory default, and resetAll() would call
        // Field.set(null, null) on a primitive — an IllegalArgumentException nothing catches.
        ConfigAccess.captureDefaultsFor(holder);
    }

    /**
     * Undo a {@link #registerHolder}. Exists for tests — a registration is process-global, and a test that
     * leaves one behind changes the option list every later test sees. Nothing in production unregisters.
     *
     * <p>Not a full undo: it does not remove the {@code ConfigAccess.DEFAULTS} entries
     * {@link ConfigAccess#captureDefaultsFor} added for the holder, so those keys persist in that map for
     * the JVM's lifetime. Harmless today — {@code DEFAULTS} is only ever read by key, never iterated — but a
     * test relying on the defaults map being fully clean after unregistering would not find that here.
     */
    static void unregisterHolder(Class<?> holder) {
        if (HOLDERS.remove(holder)) {
            all = null;
        }
    }

    private static List<Field> scan() {
        List<Field> out = new ArrayList<>();
        for (Class<?> holder : HOLDERS) {
            for (Field f : holder.getDeclaredFields()) {
                int mod = f.getModifiers();
                if (Modifier.isStatic(mod) && Modifier.isPublic(mod) && !Modifier.isFinal(mod)
                        && isSupported(f.getType())) {
                    out.add(f);
                }
            }
        }
        return out;
    }

    public static List<Field> all() {
        List<Field> a = all;
        if (a == null) {
            a = Collections.unmodifiableList(scan());
            all = a;
        }
        return a;
    }

    public static Field find(String name) {
        for (Field f : all()) {
            if (f.getName().equalsIgnoreCase(name)) {
                return f;
            }
        }
        return null;
    }

    public static boolean isSupported(Class<?> t) {
        return ConfigPrimitive.of(t) != null;
    }
}
