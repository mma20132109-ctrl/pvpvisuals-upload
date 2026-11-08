#version 330

#moj_import <minecraft:dynamictransforms.glsl>

in vec2 ghostUv;
in vec4 ghostColor;
out vec4 fragColor;

void main() {
    vec3 tint = ghostColor.rgb;
    float alpha;

    if (ghostUv.y > 1.5) {
        // A separate UV range marks the glowing spirit heads and drifting motes.
        vec2 p = vec2((ghostUv.x - 0.5) * 2.0, (ghostUv.y - 2.5) * 2.0);
        float radius = length(p);
        float halo = exp(-radius * 4.6);
        float core = 1.0 - smoothstep(0.08, 0.52, radius);
        alpha = ghostColor.a * clamp(halo * 0.48 + core * 0.78, 0.0, 1.0);
        tint = mix(tint, vec3(1.0), core * 0.78);
    } else {
        // The U axis runs across the ribbon; its centre is bright and its edges dissolve softly.
        float across = abs(ghostUv.x * 2.0 - 1.0);
        float core = 1.0 - smoothstep(0.04, 0.42, across);
        float aura = exp(-across * 4.7);
        alpha = ghostColor.a * clamp(aura * 0.72 + core * 0.28, 0.0, 1.0);
        tint = mix(tint, vec3(1.0), core * 0.60);
    }

    alpha *= ColorModulator.a;
    if (alpha < 0.002) {
        discard;
    }

    fragColor = vec4(tint * ColorModulator.rgb, alpha);
}
