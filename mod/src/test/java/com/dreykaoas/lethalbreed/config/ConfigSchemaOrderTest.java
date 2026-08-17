package com.dreykaoas.lethalbreed.config;

import com.dreykaoas.lethalbreed.config.schema.ConfigSchema;

import com.dreykaoas.lethalbreed.config.io.ConfigWriter;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Characterization: the option set and its order are the on-disk contract.
 *
 * <p>{@link ConfigWriter} groups options by category and, within a category, keeps the order
 * {@link ConfigSchema#all()} returns — which is holder order, then source-declaration order. Splitting a
 * holder, reordering {@code ConfigSchema.HOLDERS} or moving a field therefore rewrites every user's config
 * file. This pins all of them so such a change cannot happen by accident.
 *
 * <p>The expected list lives in {@code src/test/resources/config-option-order.txt} rather than inline: a list
 * of names is data, not code, and a data file keeps this class readable and regenerable. Deliberately no
 * count is quoted here — the last one said 305 while the file held 336.
 */
class ConfigSchemaOrderTest {

    private static final String RESOURCE = "/config-option-order.txt";

    @Test
    void optionSetAndOrderAreStable() throws Exception {
        List<String> actual = new ArrayList<>();
        for (Field f : ConfigSchema.all()) {
            actual.add(f.getName());
        }
        assertEquals(expected(), actual);
    }

    private static List<String> expected() throws Exception {
        List<String> names = new ArrayList<>();
        try (InputStream in = ConfigSchemaOrderTest.class.getResourceAsStream(RESOURCE)) {
            assertNotNull(in, RESOURCE + " missing from the test classpath");
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line = r.readLine(); line != null; line = r.readLine()) {
                String name = line.trim();
                if (!name.isEmpty() && !name.startsWith("#")) {
                    names.add(name);
                }
            }
        }
        return names;
    }
}
