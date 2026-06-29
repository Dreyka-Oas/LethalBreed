package com.dreykaoas.lethalbreed.config;

import com.dreykaoas.lethalbreed.config.domain.CombatMoveConfig;
import com.dreykaoas.lethalbreed.config.domain.ContaminationConfig;
import com.dreykaoas.lethalbreed.config.domain.FlowConfig;
import com.dreykaoas.lethalbreed.config.domain.ProgressionConfig;
import com.dreykaoas.lethalbreed.config.domain.SchedulerConfig;
import com.dreykaoas.lethalbreed.config.domain.TargetingConfig;
import com.dreykaoas.lethalbreed.config.domain.WorldSpawnConfig;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
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
            ProgressionConfig.class,
            ContaminationConfig.class,
    };

    /** Editable fields in source-declaration order, across all holders. */
    public static List<Field> all() {
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
                || t == double.class || t == float.class;
    }
}
