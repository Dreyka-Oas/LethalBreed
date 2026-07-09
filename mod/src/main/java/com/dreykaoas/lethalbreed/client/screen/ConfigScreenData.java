package com.dreykaoas.lethalbreed.client.screen;

import com.dreykaoas.lethalbreed.config.ConfigFields;

import java.util.ArrayList;
import java.util.List;

/** Parses the server config snapshot ({@code namekindvaluedefaultcategory} per line). */
public final class ConfigScreenData {
    private ConfigScreenData() {}

    public record Row(String name, String kind, String value, String def, String category) {}

    public static List<Row> parse(String data) {
        List<Row> out = new ArrayList<>();
        for (String line : data.split("\n")) {
            if (line.isEmpty()) {
                continue;
            }
            String[] p = line.split(String.valueOf(ConfigFields.SEP), -1);
            if (p.length == 5) {
                out.add(new Row(p[0], p[1], p[2], p[3], p[4]));
            }
        }
        return out;
    }
}
