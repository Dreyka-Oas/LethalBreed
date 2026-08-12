package com.dreykaoas.lethalbreed.lang;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two shipped lang files must always carry the exact same key set, and neither may regain a dev/debug
 * key — Task 10 dropped 35 of those by hand, with nothing but an ad-hoc script standing guard.
 *
 * <p>Read as classpath resources (not from the source tree, unlike {@code MixinConfigTest}): the question
 * here is what actually ends up in the jar's resources, and {@code assets/lethalbreed/lang/*.json} reaches
 * the test classpath unmodified because {@code src/main/resources} is on it by default.
 *
 * <p>Parsed with Gson, already a runtime dependency of Minecraft/Fabric and already used the same way by
 * {@link com.dreykaoas.lethalbreed.MixinConfigTest} — no new dependency needed.
 */
class LangFileParityTest {

    private static final String EN = "/assets/lethalbreed/lang/en_us.json";
    private static final String FR = "/assets/lethalbreed/lang/fr_fr.json";

    /**
     * A key that must never ship: the "Dev" config-tab category, or an {@code option}/{@code option.*.desc}
     * key whose field name (the segment right after {@code option.}) starts with {@code dev} or {@code
     * debug} — the naming convention every dev-only config field in this codebase follows. Field names are
     * camelCase, so the prefix check is case-sensitive: it must not also flag a coincidentally-similar player
     * option name.
     */
    private static final Pattern DEV_KEY = Pattern.compile(
            "^lethalbreed\\.category\\.Dev$|^lethalbreed\\.option\\.(?:dev|debug)[A-Za-z0-9]*(?:\\.desc)?$");

    /** Real player options this branch nearly lost (gpuDeviceIndex) or actually fixed (contamDevTimeScale) —
     *  despite its dev-ish-looking name, contamDevTimeScale is a shipped player-facing option, not a dev key;
     *  it fails the {@link #DEV_KEY} pattern because its field name starts with {@code contam}, not {@code
     *  dev}. Both must keep a real translation in both languages. */
    private static final String[] MUST_SURVIVE = {
            "lethalbreed.option.gpuDeviceIndex",
            "lethalbreed.option.contamDevTimeScale",
    };

    private static JsonObject load(String resource) throws IOException {
        try (InputStream in = LangFileParityTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, resource + " missing from the test classpath");
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }

    @Test
    void bothLangFilesParseAsJsonObjects() throws IOException {
        assertNotNull(load(EN), EN + " did not parse as a JSON object");
        assertNotNull(load(FR), FR + " did not parse as a JSON object");
    }

    @Test
    void keySetsAreIdentical() throws IOException {
        Set<String> en = load(EN).keySet();
        Set<String> fr = load(FR).keySet();

        Set<String> onlyInEn = new TreeSet<>(en);
        onlyInEn.removeAll(fr);
        Set<String> onlyInFr = new TreeSet<>(fr);
        onlyInFr.removeAll(en);

        assertTrue(onlyInEn.isEmpty() && onlyInFr.isEmpty(),
                "lang key sets diverged - present in en_us.json but missing from fr_fr.json: " + onlyInEn
                        + "; present in fr_fr.json but missing from en_us.json: " + onlyInFr);
    }

    @Test
    void neitherFileHasRegainedADevOrDebugKey() throws IOException {
        for (String resource : new String[]{EN, FR}) {
            Set<String> devKeys = new TreeSet<>();
            for (String key : load(resource).keySet()) {
                if (DEV_KEY.matcher(key).matches()) {
                    devKeys.add(key);
                }
            }
            assertTrue(devKeys.isEmpty(), resource + " has regained dev/debug key(s), which must never ship: "
                    + devKeys);
        }
    }

    @Test
    void theTwoOptionsThisBranchAlmostLostHaveRealValuesInBothFiles() throws IOException {
        for (String resource : new String[]{EN, FR}) {
            JsonObject obj = load(resource);
            for (String key : MUST_SURVIVE) {
                assertTrue(obj.has(key), resource + " is missing " + key);
                String value = obj.get(key).getAsString();
                assertFalse(value.isBlank(), resource + "'s " + key + " is blank");
            }
        }
    }
}
