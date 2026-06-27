package com.dreykaoas.lethalbreed.ai;

/**
 * Level of detail for zombie ticking. Controls how much work each zombie does based on
 * distance to the nearest player.
 */
public enum LODLevel {
    /** &lt; 32 blocks: flow field + goals + sound + block ops. */
    HIGH,
    /** 32–64 blocks: flow field navigation only, no block ops. */
    MEDIUM,
    /** 64–128 blocks: vanilla wander, ticked 1/8. */
    LOW,
    /** &gt; 128 blocks: not ticked by the mod. */
    FROZEN
}
