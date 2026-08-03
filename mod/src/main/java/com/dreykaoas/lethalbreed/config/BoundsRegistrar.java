package com.dreykaoas.lethalbreed.config;

/**
 * How a bounds group in {@code config.bounds} hands its clamp ranges to {@link ConfigBoundsTable}.
 *
 * <p>A callback rather than a shared map: the group classes need no access to the table's package-private
 * {@code Range} record, and cannot read or overwrite one another's entries.
 *
 * <p>It lives here rather than beside the groups so that {@code config/bounds} holds exactly the eight domain
 * groups and nothing else.
 */
@FunctionalInterface
public interface BoundsRegistrar {
    /** Register an inclusive clamp range for one option. Name matching is case-insensitive. */
    void b(String name, double min, double max);
}
