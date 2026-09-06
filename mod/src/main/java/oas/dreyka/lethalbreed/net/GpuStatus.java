package oas.dreyka.lethalbreed.net;

import net.minecraft.network.chat.Component;

/**
 * Which compute path the flow-field solver is on, shown live on the {@code useGpu} row of the config
 * screen.
 *
 * <p>It travels as a token ({@code ACTIVE:AMD Radeon RX 9060 XT}), never as a sentence: the server
 * detects, the client words it. Sending the rendered sentence shipped the server's locale to everyone,
 * so a French host printed "Aucun GPU" on an English client.
 *
 * <p>A carrier only. Reading the live compute state belongs to whoever already talks to the solver
 * ({@code LethalConfigCommand}); doing it here would couple the wire format to the engine.
 */
public record GpuStatus(Kind kind, String device) {

    public enum Kind {
        DISABLED("lethalbreed.gpu.disabled"),
        UNINITIALIZED("lethalbreed.gpu.uninitialized"),
        UNAVAILABLE("lethalbreed.gpu.unavailable"),
        ACTIVE("lethalbreed.gpu.active");

        private final String translationKey;

        Kind(String translationKey) {
            this.translationKey = translationKey;
        }
    }

    public String encode() {
        return kind.name() + ":" + device;
    }

    /** Null for a token this build cannot read, which is what a mismatched server version sends; the
     *  screen then falls back to the static description of the row. */
    public static GpuStatus decode(String token) {
        int sep = token.indexOf(':');
        if (sep < 0) {
            return null;
        }
        for (Kind k : Kind.values()) {
            if (k.name().equals(token.substring(0, sep))) {
                return new GpuStatus(k, token.substring(sep + 1));
            }
        }
        return null;
    }

    public Component text() {
        return kind == Kind.ACTIVE
                ? Component.translatable(kind.translationKey, device)
                : Component.translatable(kind.translationKey);
    }
}
