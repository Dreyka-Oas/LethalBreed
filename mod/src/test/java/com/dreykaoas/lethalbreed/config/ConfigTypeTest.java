package com.dreykaoas.lethalbreed.config;

import com.dreykaoas.lethalbreed.config.schema.ConfigType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless characterization of {@link ConfigType} after routing {@code NumOptionEntry.isValid} through
 * {@link ConfigType#isValidNumber} — pins that validation accepts exactly what {@link ConfigType#parse} does,
 * plus the primitive parse + CSV round-trip. No Minecraft types touched.
 */
class ConfigTypeTest {

    @Test
    void parsesEachPrimitive() {
        assertEquals(Boolean.TRUE, ConfigType.parse(boolean.class, "true"));
        assertEquals(Boolean.FALSE, ConfigType.parse(boolean.class, "0"));
        assertEquals(5, ConfigType.parse(int.class, " 5 "));
        assertEquals(9L, ConfigType.parse(long.class, "9"));
        assertEquals(3.14, ConfigType.parse(double.class, "3.14"));
    }

    @Test
    void parsesListWithAndWithoutBrackets() {
        assertArrayEquals(new double[]{1, 2, 3}, (double[]) ConfigType.parse(double[].class, "1, 2, 3"));
        assertArrayEquals(new double[]{1, 2, 3}, (double[]) ConfigType.parse(double[].class, "[1,2,3]"));
        assertArrayEquals(new double[0], (double[]) ConfigType.parse(double[].class, "[]"));
    }

    @Test
    void parseRejectsMalformed() {
        assertThrows(IllegalArgumentException.class, () -> ConfigType.parse(boolean.class, "maybe"));
        assertThrows(NumberFormatException.class, () -> ConfigType.parse(int.class, "x"));
    }

    @Test
    void csvRoundTripsThroughParse() {
        double[] arr = {1.0, 2.5, -3.0};
        assertArrayEquals(arr, (double[]) ConfigType.parse(double[].class, ConfigType.csv(arr)));
    }

    @Test
    void isValidNumberAgreesWithParse() {
        assertTrue(ConfigType.isValidNumber("int", " 5 "));
        assertFalse(ConfigType.isValidNumber("int", "x"));
        assertTrue(ConfigType.isValidNumber("long", "9"));
        assertTrue(ConfigType.isValidNumber("double", "3.14"));
        assertTrue(ConfigType.isValidNumber("list", "1, 2, 3"));
        assertFalse(ConfigType.isValidNumber("list", "1, 2, x"));
    }

    @Test
    void copyIfArrayClonesDoubleArrays() {
        double[] original = { 1.0, 2.0 };
        Object copy = ConfigType.copyIfArray(original);
        assertNotSame(original, copy);
        assertArrayEquals(original, (double[]) copy);
    }

    @Test
    void copyIfArrayReturnsScalarsUnchanged() {
        Integer boxed = 7;
        assertSame(boxed, ConfigType.copyIfArray(boxed));
        assertSame(Boolean.TRUE, ConfigType.copyIfArray(Boolean.TRUE));
    }
}
