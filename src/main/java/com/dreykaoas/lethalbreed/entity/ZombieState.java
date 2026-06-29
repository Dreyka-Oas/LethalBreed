package com.dreykaoas.lethalbreed.entity;

/** High-level behaviour state of a smart zombie (see plan.md State Machine). */
public enum ZombieState {
    IDLE,
    PURSUING_PLAYER,
    PURSUING_SOUND,
    BUILDING,
    BREAKING,
    DESCENDING
}
