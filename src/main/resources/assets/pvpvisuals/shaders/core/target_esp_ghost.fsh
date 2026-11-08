#version 330

#moj_import <minecraft:dynamictransforms.glsl>

in vec2 ghostUv;
in vec4 ghostColor;
out vec4 fragColor;

void main() {
    vec3 tint = ghostColor.rgb;
    float alpha;

    if (ghostUv.y > 3.5) {
        // Main spirit: a soft glowing head with a narrow, wavering flame tail.
        vec2 p = vec2((ghostUv.x - 0.5) * 2.0, (ghostUv.y - 4.5) * 2.0);
        float headRadius = length(vec2(p.x * 0.94, (p.y - 0.20) * 0.92));
        float head = 1.0 - smoothstep(0.34, 0.53, headRadius);

        float tailT = clamp((p.y + 0.95) / 0.88, 0.0, 1.0);
        float tailHalfWidth = mix(0.065, 0.235, smoothstep(0.0, 1.0, tailT));
        tailHalfWidth += 0.025 * sin(p.y * 13.0);
        float tailRegion = smoothstep(-0.99, -0.80, p.y)
                * (1.0 - smoothstep(-0.16, 0.02, p.y));
        float tail = (1.0 - smoothstep(0.0, 0.095, abs(p.x) - tailHalfWidth)) * tailRegion;

        float body = max(head, tail);
        float aura = exp(-headRadius * 3.6) * 0.46 + tail * 0.26;
        float hotCore = 1.0 - smoothstep(0.05, 0.34, headRadius);
        tint = mix(tint, vec3(1.0), hotCore * 0.82);
        alpha = ghostColor.a * max(body * 0.92, aura * 0.52);
    } else if (ghostUv.y > 1.5) {
        // Small trail motes stay rounded and dissolve individually; they never join into bands.
        vec2 p = vec2((ghostUv.x - 0.5) * 2.0, (ghostUv.y - 2.5) * 2.0);
        float radius = length(p);
        float halo = exp(-radius * 4.8);
        float core = 1.0 - smoothstep(0.08, 0.52, radius);
        alpha = ghostColor.a * clamp(halo * 0.48 + core * 0.76, 0.0, 1.0);
        tint = mix(tint, vec3(1.0), core * 0.72);
    } else {
        discard;
    }

    alpha *= ColorModulator.a;
    if (alpha < 0.002) {
        discard;
    }

    fragColor = vec4(tint * ColorModulator.rgb, alpha);
}
