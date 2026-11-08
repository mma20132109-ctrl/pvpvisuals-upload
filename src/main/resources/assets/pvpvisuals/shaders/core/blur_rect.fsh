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

    // Sample the captured scene in screen space. A broad, normalized kernel
    // diffuses busy backgrounds while preserving recognizable color and motion.
    vec2 texSize = vec2(textureSize(Sampler0, 0));
    vec2 uv = gl_FragCoord.xy / texSize;
    vec2 px = 1.0 / texSize;

    // The signed-distance gradient is the local surface normal. Refraction is
    // concentrated near the curved perimeter instead of smearing the whole pane.
    vec2 gradient = vec2(dFdx(distance), dFdy(distance));
    vec2 normal = gradient / max(length(gradient), 0.0001);
    float insideDepth = max(-distance, 0.0);
    float edgeBand = 1.0 - smoothstep(0.0, 5.5, insideDepth);
    float edgeFresnel = exp(-insideDepth * 0.34);
    vec2 refractedUv = uv + normal * px * (0.25 + edgeBand * 2.15);

    // 13-tap multi-radius diffusion: a soft broad lobe plus a sharper center.
    // It stays in a single pass, avoiding extra framebuffer swaps per widget.
    const float blurStep = 6.0;
    vec3 blurred = texture(Sampler0, refractedUv).rgb * 0.24;
    blurred += texture(Sampler0, refractedUv + vec2( px.x * blurStep, 0.0)).rgb * 0.105;
    blurred += texture(Sampler0, refractedUv + vec2(-px.x * blurStep, 0.0)).rgb * 0.105;
    blurred += texture(Sampler0, refractedUv + vec2(0.0,  px.y * blurStep)).rgb * 0.105;
    blurred += texture(Sampler0, refractedUv + vec2(0.0, -px.y * blurStep)).rgb * 0.105;
    blurred += texture(Sampler0, refractedUv + vec2( px.x * blurStep,  px.y * blurStep)).rgb * 0.055;
    blurred += texture(Sampler0, refractedUv + vec2(-px.x * blurStep, -px.y * blurStep)).rgb * 0.055;
    blurred += texture(Sampler0, refractedUv + vec2( px.x * blurStep, -px.y * blurStep)).rgb * 0.055;
    blurred += texture(Sampler0, refractedUv + vec2(-px.x * blurStep,  px.y * blurStep)).rgb * 0.055;
    blurred += texture(Sampler0, refractedUv + vec2( 2.0 * px.x * blurStep, 0.0)).rgb * 0.03;
    blurred += texture(Sampler0, refractedUv + vec2(-2.0 * px.x * blurStep, 0.0)).rgb * 0.03;
    blurred += texture(Sampler0, refractedUv + vec2(0.0,  2.0 * px.y * blurStep)).rgb * 0.03;
    blurred += texture(Sampler0, refractedUv + vec2(0.0, -2.0 * px.y * blurStep)).rgb * 0.03;

    // A small reduction in background saturation gives text and symbols a
    // cleaner contrast without turning the material into an opaque gray plate.
    float luminance = dot(blurred, vec3(0.2126, 0.7152, 0.0722));
    vec3 diffused = mix(blurred, vec3(luminance), 0.045);
    float tintAmount = clamp(vertexColor.a * 1.15, 0.0, 0.48);
    vec3 glass = mix(diffused, vertexColor.rgb, tintAmount);

    // iOS 27-inspired optical stack: a darkened inner edge, a lifted lower
    // reflection, and narrow specular light across the curved upper lip.
    float vertical = clamp(localPos.y / max(rectSize.y, 1.0), 0.0, 1.0);
    float horizontal = clamp(localPos.x / max(rectSize.x, 1.0), 0.0, 1.0);
    float sideDistance = abs(horizontal - 0.5) * 2.0;
    float upperGlow = exp(-pow((vertical - 0.045) / 0.075, 2.0)) * 0.105;
    float reflectedBand = exp(-pow((vertical - (0.115 + sideDistance * 0.025)) / 0.070, 2.0)) * 0.038;
    float specularLineY = 0.026 + sideDistance * 0.035;
    float specular = exp(-pow((vertical - specularLineY) / 0.012, 2.0))
            * (0.12 + (1.0 - sideDistance) * 0.12);
    float lowerBounce = exp(-pow((vertical - 0.965) / 0.035, 2.0)) * 0.025;
    glass *= 1.0 - edgeFresnel * 0.13;
    glass -= vec3(1.0) * (1.0 - smoothstep(0.0, 14.0, insideDepth)) * 0.018;
    glass += vec3(1.0) * (upperGlow + reflectedBand + specular + lowerBounce + edgeBand * 0.045);

    // Selective RGB separation is visible only at the refracting rim.
    vec2 chromaOffset = normal * px * edgeBand * 1.35;
    float redEdge = texture(Sampler0, refractedUv + chromaOffset).r;
    float blueEdge = texture(Sampler0, refractedUv - chromaOffset).b;
    glass.r = mix(glass.r, redEdge, edgeBand * 0.10);
    glass.b = mix(glass.b, blueEdge, edgeBand * 0.10);

    glass += (hash(gl_FragCoord.xy) - 0.5) * 0.005;
    fragColor = vec4(clamp(glass, 0.0, 1.0), mask * 0.985) * ColorModulator;
}
