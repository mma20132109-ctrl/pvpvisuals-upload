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
    float radius = clamp(params.x, 0.0, min(halfSize.x, halfSize.y));
    float distance = sdRoundBox(localPos - halfSize, halfSize, radius);
    float aa = max(fwidth(distance), 0.75);
    float mask = clamp(0.5 - distance / aa, 0.0, 1.0);
    if (mask <= 0.002) discard;

    // The captured scene is sampled in screen space. A compact 13-tap
    // Gaussian kernel softens the backdrop without requiring another framebuffer.
    vec2 texSize = vec2(textureSize(Sampler0, 0));
    vec2 uv = gl_FragCoord.xy / texSize;
    vec2 px = 1.0 / texSize;

    // Slight edge refraction gives the panel a lens-like glass boundary.
    vec2 gradient = normalize(vec2(dFdx(distance), dFdy(distance)) + vec2(1e-5));
    float edge = 1.0 - smoothstep(-2.0, 1.0, distance);
    vec2 refractedUv = uv + gradient * px * edge * 1.85;

    // Blur in framebuffer pixels (not GUI-scaled pixels). The previous 1-2px
    // kernel was too subtle at common resolutions and made the glass look sharp.
    // A wider weighted kernel keeps the effect confined to this rounded panel.
    const float radius = 6.0;
    vec3 blurred = texture(Sampler0, refractedUv).rgb * 0.20;
    blurred += texture(Sampler0, refractedUv + vec2( px.x * radius, 0.0)).rgb * 0.12;
    blurred += texture(Sampler0, refractedUv + vec2(-px.x * radius, 0.0)).rgb * 0.12;
    blurred += texture(Sampler0, refractedUv + vec2(0.0,  px.y * radius)).rgb * 0.12;
    blurred += texture(Sampler0, refractedUv + vec2(0.0, -px.y * radius)).rgb * 0.12;
    blurred += texture(Sampler0, refractedUv + vec2( px.x * radius,  px.y * radius)).rgb * 0.07;
    blurred += texture(Sampler0, refractedUv + vec2(-px.x * radius, -px.y * radius)).rgb * 0.07;
    blurred += texture(Sampler0, refractedUv + vec2( px.x * radius, -px.y * radius)).rgb * 0.07;
    blurred += texture(Sampler0, refractedUv + vec2(-px.x * radius,  px.y * radius)).rgb * 0.07;
    blurred += texture(Sampler0, refractedUv + vec2( 2.0 * px.x * radius, 0.0)).rgb * 0.01;
    blurred += texture(Sampler0, refractedUv + vec2(-2.0 * px.x * radius, 0.0)).rgb * 0.01;
    blurred += texture(Sampler0, refractedUv + vec2(0.0,  2.0 * px.y * radius)).rgb * 0.01;
    blurred += texture(Sampler0, refractedUv + vec2(0.0, -2.0 * px.y * radius)).rgb * 0.01;

    float tintAmount = clamp(vertexColor.a * 1.15, 0.0, 0.48);
    vec3 glass = mix(blurred, vertexColor.rgb, tintAmount);

    // Layered reflections make the surface read as glass instead of a plain alpha panel.
    float vertical = clamp(localPos.y / max(rectSize.y, 1.0), 0.0, 1.0);
    float upperSheen = pow(1.0 - vertical, 2.1);
    float reflectionBand = exp(-pow((vertical - 0.105) / 0.060, 2.0));
    float fresnel = pow(1.0 - clamp(abs(distance) / max(radius, 1.0), 0.0, 1.0), 1.6);
    float bottomShade = vertical * 0.035;
    glass += vec3(1.0) * (upperSheen * 0.075 + reflectionBand * 0.045 + fresnel * edge * 0.075);
    glass -= vec3(bottomShade);
    // A restrained chromatic fringe appears only on the curved perimeter.
    vec2 fringe = gradient * px * edge * 0.75;
    float redEdge = texture(Sampler0, refractedUv + fringe).r;
    float blueEdge = texture(Sampler0, refractedUv - fringe).b;
    glass.r = mix(glass.r, redEdge, edge * 0.055);
    glass.b = mix(glass.b, blueEdge, edge * 0.055);
    glass += (hash(gl_FragCoord.xy) - 0.5) * 0.006;

    fragColor = vec4(clamp(glass, 0.0, 1.0), mask * 0.97) * ColorModulator;
}
