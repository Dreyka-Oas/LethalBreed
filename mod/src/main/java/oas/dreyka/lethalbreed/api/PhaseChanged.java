package oas.dreyka.lethalbreed.api;

/** Told when the night progression moves, with the phase left behind and the one just reached. */
@FunctionalInterface
public interface PhaseChanged {
    void onPhaseChanged(int from, int to);
}
