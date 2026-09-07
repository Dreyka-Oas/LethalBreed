package oas.dreyka.lethalbreed.api.event;

/**
 * The five moods a zombie can hold, published so that {@link MoodCallback} never has to name an internal
 * class.
 *
 * <p>The names and their order match {@code MoodStateDispatch.State} exactly, and a test in the mood package
 * fails the build if they ever drift apart: the conversion between the two is by ordinal, which is free at
 * runtime and silently wrong the day somebody inserts a sixth constant in the middle of one of them.
 *
 * <p>Five, and no more. A listener substitutes a mood, it does not invent one.
 */
public enum MoodState { NORMAL, FLEEING, SHELTERING, CELEBRATING, SLEEPING }
