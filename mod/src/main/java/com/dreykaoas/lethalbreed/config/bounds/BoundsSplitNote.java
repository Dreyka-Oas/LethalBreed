package com.dreykaoas.lethalbreed.config.bounds;

/**
 * Shared rationale for every {@code *Bounds} class in this package and {@code config.bounds.engine}: each
 * was split out of {@code ConfigBoundsTable}, which had grown to 305 append-only lines across nine
 * unrelated domains. Registration order does not matter — the table is a map keyed by lower-cased option
 * name — but grouping does: a bound belongs next to the options it governs, and {@code ConfigBoundsTest}
 * fails the build if any numeric option loses one.
 *
 * <p>Not instantiated. Exists only as a single place for each {@code *Bounds} class's Javadoc to point at.
 */
public final class BoundsSplitNote {
    private BoundsSplitNote() {}
}
