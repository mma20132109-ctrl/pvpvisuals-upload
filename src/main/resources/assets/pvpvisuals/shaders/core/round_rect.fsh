#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec4 vertexColor;
in vec2 localPos;
flat in vec2 rectSize;
flat in vec2 params;

out vec4 fragColor;

float sdRoundBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

float dither(vec2 co) {
    return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453) - 0.5;
}

void main() {
    vec2 halfSize = rectSize * 0.5;
    float radius = clamp(params.x, 0.0, min(halfSize.x, halfSize.y));
    float distance = sdRoundBox(localPos - halfSize, halfSize, radius);
    float aa = max(fwidth(distance), 0.75);
    float outerMask = clamp(0.5 - distance / aa, 0.0, 1.0);

    float borderWidth = max(params.y, 0.0);
    float innerMask = 1.0;
    if (borderWidth > 0.0) {
        innerMask = clamp(0.5 - (distance + borderWidth) / aa, 0.0, 1.0);
    }
    float fillMask = outerMask * innerMask;

    vec4 color = vertexColor;
    float vertical = clamp(localPos.y / max(rectSize.y, 1.0), 0.0, 1.0);
    float topLight = (1.0 - vertical) * 0.045;
    color.rgb = clamp(color.rgb + vec3(topLight), 0.0, 1.0);
    color.a *= fillMask;
    if (color.a <= 0.002) discard;

    color.rgb += dither(gl_FragCoord.xy) / 255.0;
    fragColor = color * ColorModulator;
}
