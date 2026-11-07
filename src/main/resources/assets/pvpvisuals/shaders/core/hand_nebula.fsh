#version 330

// Hand Shader: полноэкранный проход после отрисовки рук. Рука и предмет определяются по глубине: перед проходом
// рук буфер глубины очищается (1.0), поэтому пиксели с глубиной < 1 это ровно предмет/рука. Раскладка uniform
// (std140) совпадает с HandShaderRenderer.
uniform sampler2D Depth;

layout(std140) uniform EchoHandData {
    vec4 ColorA;  // rgb + alpha основного цвета
    vec4 ColorB;  // rgb + alpha второго цвета
    vec4 Params;  // x: время анимации (секунды * скорость), y: ширина экрана, z: высота экрана, w: 0
};

in vec2 vUv;
out vec4 fragColor;

float depthAt(ivec2 p) {
    return texelFetch(Depth, p, 0).r;
}

float hash13(vec3 p3) {
    p3 = fract(p3 * 0.1031);
    p3 += dot(p3, p3.zyx + 31.32);
    return fract((p3.x + p3.y) * p3.z);
}

float vnoise(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash13(i), hash13(i + vec3(1, 0, 0)), f.x),
                   mix(hash13(i + vec3(0, 1, 0)), hash13(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(hash13(i + vec3(0, 0, 1)), hash13(i + vec3(1, 0, 1)), f.x),
                   mix(hash13(i + vec3(0, 1, 1)), hash13(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}

float fbm(vec3 p) {
    float a = 0.5;
    float s = 0.0;
    for (int i = 0; i < 4; i++) {
        s += a * vnoise(p);
        p = p * 2.03 + vec3(11.7, 3.1, 7.9);
        a *= 0.5;
    }
    return s / 0.9375;
}

// Звёзды в ячейках экрана: у каждой своя «жизнь» (загорается, горит, гаснет) и мерцание.
float starLayer(vec2 uv, float cells, float seed, float density, float time) {
    vec2 g = uv * cells;
    vec2 id = floor(g);
    vec2 f = fract(g) - 0.5;
    float h = hash13(vec3(id, seed));
    if (h > density) {
        return 0.0;
    }
    vec2 jitter = vec2(hash13(vec3(id, seed + 1.7)), hash13(vec3(id, seed + 3.3))) - 0.5;
    float r = length(f - jitter * 0.6);
    float h2 = hash13(vec3(id, seed + 5.1));
    float life = fract(time * (0.06 + 0.10 * h2) + h * 7.0);
    float fade = smoothstep(0.0, 0.18, life) * (1.0 - smoothstep(0.55, 1.0, life));
    float tw = 0.65 + 0.35 * sin(time * (2.0 + 4.0 * h2) + h * 40.0);
    float size = 0.07 + 0.06 * h2;
    return (exp(-(r * r) / (size * size)) + exp(-r * 3.5) * 0.15) * fade * tw;
}

void main() {
    ivec2 px = ivec2(gl_FragCoord.xy);
    float d = depthAt(px);
    if (d >= 0.99999) {
        discard; // мир, а не рука/предмет
    }
    // край силуэта: рядом есть пиксель мира -> подсвечиваем кромку
    float rim = 0.0;
    rim += depthAt(px + ivec2(2, 0)) >= 0.99999 ? 1.0 : 0.0;
    rim += depthAt(px - ivec2(2, 0)) >= 0.99999 ? 1.0 : 0.0;
    rim += depthAt(px + ivec2(0, 2)) >= 0.99999 ? 1.0 : 0.0;
    rim += depthAt(px - ivec2(0, 2)) >= 0.99999 ? 1.0 : 0.0;
    rim = clamp(rim * 0.5, 0.0, 1.0);

    float t = Params.x;
    float aspect = Params.y / max(Params.z, 1.0);
    // Координаты узора: экран + глубина (поверхность «следует» за формой предмета, а не плоский экран)
    vec3 p = vec3(vUv.x * aspect, vUv.y, 0.0) * 3.0 + vec3(0.0, 0.0, d * 400.0);
    // NEBULA: яркий переливающийся градиент двух цветов, возмущённый шумом
    float g = fbm(p * 0.8 + vec3(t * 0.07, t * 0.05, -t * 0.06));
    float wave = 0.5 + 0.5 * sin(dot(p, vec3(1.0, 0.7, 0.4)) * 1.4 + t * 0.7 + g * 3.0);
    vec3 rgb = mix(ColorA.rgb, ColorB.rgb, wave);
    rgb *= 0.65 + 0.9 * g;
    rgb += 0.25 * pow(g, 3.0);
    float a = clamp(mix(ColorA.a, ColorB.a, wave) * (0.7 + 0.5 * g) + rim * 0.3, 0.0, 1.0);
    fragColor = vec4(rgb, a);
}
