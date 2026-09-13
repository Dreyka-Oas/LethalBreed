package oas.dreyka.lethalbreed.entity;

/** High-level behaviour state of a smart zombie. One at a time, held on {@link ZombieStateAttachment}. */
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
    /** Dozing by day (head bowed). Appended LAST so existing ordinals stay stable: the ordinal is stored in
     *  {@code ZombieStateAttachment.STATE}, read server-side to keep a sleeper silent. */
    SLEEPING,
    /** Bomber fuse lit, frozen in place until detonation, like a Creeper. Appended LAST, same reason as
     *  SLEEPING: the ordinal is persisted. */
    ARMED,
    /** Latched onto the victim it landed on, riding it while it gnaws. Appended LAST, same reason as
     *  SLEEPING: the ordinal is persisted. */
    CLINGING
}
