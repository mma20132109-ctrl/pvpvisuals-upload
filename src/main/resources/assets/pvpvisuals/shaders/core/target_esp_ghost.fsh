#version 330

#moj_import <minecraft:dynamictransforms.glsl>

in vec2 ghostUv;
in vec4 ghostColor;
out vec4 fragColor;

const float PI = 3.14159265359;

void main() {
    // Local billboard coordinates: x is left/right, y is bottom/top.
    vec2 p = vec2((ghostUv.x - 0.5) * 2.0, (0.5 - ghostUv.y) * 2.0);
    float aa = max(max(fwidth(p.x), fwidth(p.y)) * 1.35, 0.0035);

    // Rounded head and upper body.
    float capDistance = length(vec2(p.x, p.y - 0.05)) - 0.47;
    float capMask = (1.0 - smoothstep(-aa, aa, capDistance))
            * smoothstep(0.015, 0.075, p.y);

    // Soft, wavy lower edge makes the silhouette read as a little floating ghost.
    float bottomY = -0.63 + 0.075 * cos((p.x / 0.47) * 5.0 * PI);
    float lowerDistance = max(abs(p.x) - 0.47,
            max(bottomY - p.y, p.y - 0.05));
    float lowerMask = 1.0 - smoothstep(-aa, aa, lowerDistance);
    float bodyMask = max(capMask, lowerMask);

    // A wider, low-opacity copy produces a soft aura without using a circular texture.
    float glowCapDistance = length(vec2(p.x, p.y - 0.05)) - 0.59;
    float glowCap = (1.0 - smoothstep(-aa, aa, glowCapDistance))
            * smoothstep(-0.035, 0.045, p.y);
    float glowBottomY = bottomY - 0.10;
    float glowLowerDistance = max(abs(p.x) - 0.59,
            max(glowBottomY - p.y, p.y - 0.12));
    float glowLower = 1.0 - smoothstep(-aa, aa, glowLowerDistance);
    float glowMask = max(glowCap, glowLower);

    if (glowMask < 0.003) {
        discard;
    }

    // Two tiny dark eyes distinguish the ghost silhouette from a generic particle.
    float eyeLeft = 1.0 - smoothstep(0.035, 0.055,
            length(p - vec2(-0.16, -0.105)));
    float eyeRight = 1.0 - smoothstep(0.035, 0.055,
            length(p - vec2(0.16, -0.105)));
    float eyes = max(eyeLeft, eyeRight);

    float verticalLight = 0.72 + 0.28 * smoothstep(-0.66, 0.60, p.y);
    vec3 tint = ghostColor.rgb * verticalLight;
    tint = mix(tint, vec3(0.12, 0.045, 0.19), eyes * 0.88);

    float edgeDistance = min(abs(capDistance), abs(lowerDistance));
    float rim = 1.0 - smoothstep(0.0, 0.075, edgeDistance);
    tint += ghostColor.rgb * rim * 0.20;

    float alpha = ghostColor.a * max(bodyMask * 0.88, glowMask * 0.30);
    alpha *= ColorModulator.a;
    if (alpha < 0.003) {
        discard;
    }

    fragColor = vec4(tint * ColorModulator.rgb, alpha);
}
