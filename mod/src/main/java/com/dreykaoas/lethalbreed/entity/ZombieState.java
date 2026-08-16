package com.dreykaoas.lethalbreed.entity;

/** High-level behaviour state of a smart zombie (see plan.md State Machine). */
public enum ZombieState {
    IDLE,
    PURSUING_PLAYER,
    PURSUING_SOUND,
    BUILDING,
    BREAKING,
    DESCENDING,
    FLEEING,
    SHELTERING,
    CELEBRATING,
    /** Dozing by day (head bowed). Appended LAST so existing ordinals stay stable — the ordinal is stored in
     *  {@code ZombieStateAttachment.STATE}, read server-side to keep a sleeper silent. */
    SLEEPING,
    /** Bombeur fuse lit — frozen in place until detonation, like a Creeper. Appended LAST, same reason as
     *  SLEEPING: the ordinal is persisted. */
    ARMED
}
