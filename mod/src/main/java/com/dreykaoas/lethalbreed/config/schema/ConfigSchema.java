package com.dreykaoas.lethalbreed.config.schema;

import com.dreykaoas.lethalbreed.config.ConfigAccess;

import com.dreykaoas.lethalbreed.config.domain.CombatMoveConfig;
import com.dreykaoas.lethalbreed.config.domain.ContaminationConfig;
import com.dreykaoas.lethalbreed.config.domain.engine.DevTestConfig;
import com.dreykaoas.lethalbreed.config.domain.engine.ExpertConfig;
import com.dreykaoas.lethalbreed.config.domain.engine.FlowConfig;
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
 */
public final class ConfigSchema {
    private ConfigSchema() {}

    /** Every class whose public-static-non-final primitive fields are config options. */
    private static final Class<?>[] HOLDERS = {
            SchedulerConfig.class,
            FlowConfig.class,
            TargetingConfig.class,
            WorldSpawnConfig.class,
            CombatMoveConfig.class,
            // These three were one holder (ProgressionConfig). They MUST stay adjacent and in this order:
            // ConfigSchema.all() order is the on-disk write order within a category (ConfigSchemaOrderTest).
            ProgressionConfig.class,
            SpecialVariantConfig.class,
            DevTestConfig.class,
            ContaminationConfig.class,
            ZombieMoodConfig.class,
            ExpertConfig.class,
    };

    /** Editable fields in source-declaration order, across all holders. Computed once — the holder classes
     *  and their fields are fixed at compile time, so re-running reflection on every call (config load, every
     *  GUI keystroke via {@code ConfigAccess}, every {@code find()}) only rebuilt an identical list. Returned
     *  as an unmodifiable view since every caller only iterates it (audit #29). */
    private static final List<Field> ALL = Collections.unmodifiableList(scan());

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
        return ALL;
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
        return t == boolean.class || t == int.class || t == long.class
                || t == double.class || t == float.class || t == double[].class;
    }
}
