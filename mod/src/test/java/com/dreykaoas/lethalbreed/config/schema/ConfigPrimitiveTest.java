package com.dreykaoas.lethalbreed.config.schema;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConfigPrimitiveTest {

    @Test
    void matchesEachOfTheSixPrimitiveKinds() {
        assertEquals(ConfigPrimitive.BOOL, ConfigPrimitive.of(boolean.class));
        assertEquals(ConfigPrimitive.INT, ConfigPrimitive.of(int.class));
        assertEquals(ConfigPrimitive.LONG, ConfigPrimitive.of(long.class));
        assertEquals(ConfigPrimitive.DOUBLE, ConfigPrimitive.of(double.class));
        assertEquals(ConfigPrimitive.FLOAT, ConfigPrimitive.of(float.class));
        assertEquals(ConfigPrimitive.LIST, ConfigPrimitive.of(double[].class));
    }

    @Test
    void returnsNullForAnUnsupportedType() {
        assertNull(ConfigPrimitive.of(String.class));
        assertNull(ConfigPrimitive.of(Object.class));
    }

    @Test
    void labelsMatchTheHistoricalStrings() {
        assertEquals("bool", ConfigPrimitive.BOOL.label());
        assertEquals("int", ConfigPrimitive.INT.label());
        assertEquals("long", ConfigPrimitive.LONG.label());
        assertEquals("double", ConfigPrimitive.DOUBLE.label());
        assertEquals("float", ConfigPrimitive.FLOAT.label());
        assertEquals("list", ConfigPrimitive.LIST.label());
    }
}
