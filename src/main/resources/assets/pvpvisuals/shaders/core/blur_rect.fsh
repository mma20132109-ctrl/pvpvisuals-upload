#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 localPos;
flat in vec2 rectSize;
flat in vec2 params;

out vec4 fragColor;

float sdRoundBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    vec2 halfSize = rectSize * 0.5;
    float radius = min(params.x, min(halfSize.x, halfSize.y));
    float d = sdRoundBox(localPos - halfSize, halfSize, radius);
    float aa = max(fwidth(d), 1e-4);
    float mask = clamp(0.5 - d / aa, 0.0, 1.0);
    if (mask <= 0.002) discard;

    vec2 texSize = vec2(textureSize(Sampler0, 0));
    vec2 uv = gl_FragCoord.xy / texSize;
    vec2 px = 1.0 / texSize;

    vec3 blurred = texture(Sampler0, uv).rgb * 0.25;
    blurred += texture(Sampler0, uv + vec2(px.x, 0.0)).rgb * 0.125;
    blurred += texture(Sampler0, uv - vec2(px.x, 0.0)).rgb * 0.125;
    blurred += texture(Sampler0, uv + vec2(0.0, px.y)).rgb * 0.125;
    blurred += texture(Sampler0, uv - vec2(0.0, px.y)).rgb * 0.125;
    blurred += texture(Sampler0, uv + px).rgb * 0.0625;
    blurred += texture(Sampler0, uv - px).rgb * 0.0625;
    blurred += texture(Sampler0, uv + vec2(px.x, -px.y)).rgb * 0.0625;
    blurred += texture(Sampler0, uv + vec2(-px.x, px.y)).rgb * 0.0625;

    vec3 tint = vertexColor.rgb;
    vec3 color = mix(blurred, tint, clamp(vertexColor.a, 0.0, 0.35));
    color += (hash(gl_FragCoord.xy) - 0.5) * 0.012;
    fragColor = vec4(color, mask * 0.92) * ColorModulator;
}
