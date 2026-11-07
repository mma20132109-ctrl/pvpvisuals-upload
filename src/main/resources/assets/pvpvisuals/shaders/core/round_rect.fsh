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
    float radius = min(params.x, min(halfSize.x, halfSize.y));
    float distance = sdRoundBox(localPos - halfSize, halfSize, radius);
    float aa = max(fwidth(distance), 1e-4);
    float alpha = clamp(0.5 - distance / aa, 0.0, 1.0);

    if (params.y > 0.0) {
        alpha *= clamp(0.5 + (distance + params.y) / aa, 0.0, 1.0);
    }

    vec4 color = vertexColor;
    color.a *= alpha;
    if (color.a <= 0.002) discard;

    color.rgb += dither(gl_FragCoord.xy) / 255.0;
    fragColor = color * ColorModulator;
}
