package com.dreykaoas.lethalbreed.mixin.client;

/**
 * Shared rationale for every purely-presentational client mixin that sets {@code require = 0} on its
 * injection: {@code lethalbreed.mixins.json} sets {@code defaultRequire = 1}, which turns any failed
 * injection into a hard crash at load — correct for gameplay mixins, wrong for a cosmetic one. A HUD or
 * render mod that redirects or injects into the same target should cost the player a visual effect, not
 * the whole game.
 *
 * <p>Not instantiated. Exists only as a single place for the 8 client-side cosmetic mixins to point their
 * {@code require = 0} comment at, instead of repeating this paragraph in each of them.
 */
public final class PresentationalMixinNotes {
    private PresentationalMixinNotes() {}
}
