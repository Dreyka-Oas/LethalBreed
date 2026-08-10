package com.dreykaoas.lethalbreed.spatial;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class CellMathTest {

    @Test
    void packKeyIsDistinctPerCell() {
        assertNotEquals(CellMath.packKey(1, 2), CellMath.packKey(2, 1));
        assertEquals(CellMath.packKey(5, -3), CellMath.packKey(5, -3));
    }

    @Test
    void packKeyMatchesTheOriginalFormula() {
        // Pinned to the exact bit-packing both SpatialGrid and TargetIndex relied on before this
        // extraction, so an accidental formula change here is caught immediately.
        assertEquals((((long) 7) << 32) ^ (-4 & 0xffffffffL), CellMath.packKey(7, -4));
    }

    @Test
    void floorCellRoundsTowardNegativeInfinity() {
        // Math.floorDiv semantics: -1 / 16 floors to -1, not 0 (unlike a plain integer division).
        assertEquals(-1, CellMath.floorCell(-1.0, 16));
        assertEquals(-1, CellMath.floorCell(-16.0, 16));
        assertEquals(-2, CellMath.floorCell(-16.5, 16));
        assertEquals(0, CellMath.floorCell(0.0, 16));
        assertEquals(0, CellMath.floorCell(15.9, 16));
        assertEquals(1, CellMath.floorCell(16.0, 16));
    }
}
