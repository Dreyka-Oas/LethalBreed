package com.dreykaoas.lethalbreed.entity.move;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Headless characterization of the pure {@link MoveMath} helpers (no Minecraft state). */
class MoveMathTest {

    @Test
    void stepSignQuantisesToCardinal() {
        assertEquals(1, MoveMath.stepSign(0.6));
        assertEquals(1, MoveMath.stepSign(50.0));
        assertEquals(-1, MoveMath.stepSign(-0.6));
        assertEquals(-1, MoveMath.stepSign(-50.0));
        assertEquals(0, MoveMath.stepSign(0.0));
    }

    @Test
    void stepSignDeadZoneIsHalfBlockExclusive() {
        // dead-zone is |d| <= 0.5 (strict > 0.5 / < -0.5 to leave it)
        assertEquals(0, MoveMath.stepSign(0.5));
        assertEquals(0, MoveMath.stepSign(-0.5));
        assertEquals(0, MoveMath.stepSign(0.49));
        assertEquals(1, MoveMath.stepSign(0.51));
        assertEquals(-1, MoveMath.stepSign(-0.51));
    }
}
