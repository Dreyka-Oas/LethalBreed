#version 330

// Mixes the sharp scene (SharpSampler = untouched main target) with a fully-blurred copy (BlurSampler = swap,
// after the box_blur passes) using a radial mask. Near the screen centre the mask is 0 → the sharp image shows
// through crisp; past ClearRadius it ramps to 1 → the blurred copy takes over. FadeWidth controls how soft that
// ring is. ClearRadius/FadeWidth are driven per-frame from the plague level, so the clear centre shrinks and the
// blurred rim grows as the sickness worsens.

uniform sampler2D SharpSampler;
uniform sampler2D BlurSampler;

layout(std140) uniform RadialConfig {
    float ClearRadius; // fraction of the half-diagonal that stays fully sharp (0..1)
    float FadeWidth;   // fraction over which sharp fades to blurred
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    // Distance from screen centre, normalised so a corner is ~1.0 (accounts for aspect via the 0.5 half-extents).
    vec2 d = texCoord - vec2(0.5);
    float dist = length(d) / length(vec2(0.5));

    float blurAmount = smoothstep(ClearRadius, ClearRadius + FadeWidth, dist);

    vec4 sharp = texture(SharpSampler, texCoord);
    vec4 blur = texture(BlurSampler, texCoord);
    fragColor = mix(sharp, blur, blurAmount);
}
