package com.dreykaoas.lethalbreed.config;

import com.dreykaoas.lethalbreed.config.schema.ConfigSchema;

import java.util.HashMap;
import com.dreykaoas.lethalbreed.config.bounds.CombatMoveBounds;
import com.dreykaoas.lethalbreed.config.bounds.ContaminationBounds;
import com.dreykaoas.lethalbreed.config.bounds.engine.FlowBounds;
import com.dreykaoas.lethalbreed.config.bounds.ProgressionBounds;
import com.dreykaoas.lethalbreed.config.bounds.engine.PerfBounds;
import com.dreykaoas.lethalbreed.config.bounds.TargetingBounds;
import com.dreykaoas.lethalbreed.config.bounds.WorldSpawnBounds;
import com.dreykaoas.lethalbreed.config.bounds.ZombieMoodBounds;

import java.util.Locale;
import java.util.Map;

/**
 * Pure data: the sane numeric range registered for each clamped config field, keyed by lower-cased field name
 * (matching {@link ConfigSchema#find}'s case-insensitivity). No logic lives here — {@link ConfigBounds#clamp}
 * looks a field up via {@link #get} and applies the range. Split out so the (large, append-only) table grows
 * without bloating the clamp logic.
 */
final class ConfigBoundsTable {
    private ConfigBoundsTable() {}

    record Range(double min, double max) {}


    private static final Map<String, Range> BOUNDS = new HashMap<>();

    private static void b(String name, double min, double max) {
        BOUNDS.put(name.toLowerCase(Locale.ROOT), new Range(min, max));
    }

    /** Registered range for a field, or {@code null} when the field is unbounded (passes through unchanged). */
    static Range get(String name) {
        return BOUNDS.get(name.toLowerCase(Locale.ROOT));
    }

    static {
        PerfBounds.register(ConfigBoundsTable::b);
        FlowBounds.register(ConfigBoundsTable::b);
        TargetingBounds.register(ConfigBoundsTable::b);
        WorldSpawnBounds.register(ConfigBoundsTable::b);
        CombatMoveBounds.register(ConfigBoundsTable::b);
        ProgressionBounds.register(ConfigBoundsTable::b);
        ContaminationBounds.register(ConfigBoundsTable::b);
        ZombieMoodBounds.register(ConfigBoundsTable::b);
    }
}
