#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

in vec3 vBlock;
in vec3 vRel;
out vec4 fragColor;

// Тот же блок, что и в block_overlay.vsh (раскладка std140 должна совпадать), заполняется в BlockOverlayRenderer.
layout(std140) uniform EchoBlockData {
    vec4 Offset;
    vec4 BoxMin; // w: время
    vec4 BoxMax;
    vec4 Color;
    vec4 Params; // x: толщина линий (1 ~ 0.01 блока), y: только обводка 0/1, z: качество 0/1/2, w: плотность звёзд
    vec4 ColorB; // rgb: второй цвет (космические стили)
};

#define TAU 6.28318530718

// Грань по gl_PrimitiveID / 2. Возвращает ось грани, расстояние до ближайшего ребра грани (внутри > 0, во
// вынесенной кромке < 0) и координаты на грани. Грани, обращённые от камеры, отбрасываются.
void faceInfo(out int axis, out float edge, out vec2 uv) {
    int face = gl_PrimitiveID / 2;
    axis = face / 2;
    float sgn = (face % 2 == 0) ? -1.0 : 1.0;
    vec3 n = vec3(0.0);
    n[axis] = sgn;
    if (dot(n, vRel) >= 0.0) {
        discard;
    }
    int a = (axis + 1) % 3;
    int b = (axis + 2) % 3;
    float dA = min(vBlock[a] - BoxMin[a], BoxMax[a] - vBlock[a]);
    float dB = min(vBlock[b] - BoxMin[b], BoxMax[b] - vBlock[b]);
    edge = min(dA, dB);
    uv = vec2(vBlock[a], vBlock[b]);
}

// 1 у ребра, 0 дальше толщины w (в блоках).
float edgeMask(float edge, float w) {
    return 1.0 - smoothstep(w * 0.6, w, edge);
}

void finish(vec3 rgb, float a) {
    if (a < 0.004) {
        discard;
    }
    fragColor = vec4(rgb, clamp(a, 0.0, 1.0));
}


// ---------------------------------------------------------------------------------------------------------------
// Шум. Всё считается от 3D-позиции внутри блока (vBlock), поэтому узор непрерывен на рёбрах между гранями.
// ---------------------------------------------------------------------------------------------------------------

// Дешёвый хэш (Dave Hoskins, "Hash without Sine"): 3D-вектор -> псевдослучайное число 0..1 без sin().
float hash13(vec3 p3) {
    p3 = fract(p3 * 0.1031);
    p3 += dot(p3, p3.zyx + 31.32);
    return fract((p3.x + p3.y) * p3.z);
}

// Value noise: хэш в 8 углах ячейки + трилинейная интерполяция со сглаживанием f*f*(3-2f) (без «ступенек»).
float vnoise(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    float n000 = hash13(i);
    float n100 = hash13(i + vec3(1.0, 0.0, 0.0));
    float n010 = hash13(i + vec3(0.0, 1.0, 0.0));
    float n110 = hash13(i + vec3(1.0, 1.0, 0.0));
    float n001 = hash13(i + vec3(0.0, 0.0, 1.0));
    float n101 = hash13(i + vec3(1.0, 0.0, 1.0));
    float n011 = hash13(i + vec3(0.0, 1.0, 1.0));
    float n111 = hash13(i + vec3(1.0, 1.0, 1.0));
    return mix(mix(mix(n000, n100, f.x), mix(n010, n110, f.x), f.y),
               mix(mix(n001, n101, f.x), mix(n011, n111, f.x), f.y), f.z);
}

// fBm: сумма октав шума, каждая вдвое мельче и вдвое слабее. Число октав зависит от качества (2/3/5),
// результат нормирован к диапазону 0..1 (сумма амплитуд = 1 - 0.5^октав).
float fbm(vec3 p, int oct) {
    float a = 0.5;
    float s = 0.0;
    for (int i = 0; i < 5; i++) {
        if (i >= oct) {
            break;
        }
        s += a * vnoise(p);
        p = p * 2.03 + vec3(11.7, 3.1, 7.9); // сдвиг между октавами, чтобы они не совпадали
        a *= 0.5;
    }
    return s / (1.0 - pow(0.5, float(oct)));
}

// Качество: 0 - LOW (2 октавы), 1 - MEDIUM (3), 2 - HIGH (5 + искажение координат).
int octaves() {
    return Params.z < 0.5 ? 2 : (Params.z < 1.5 ? 3 : 5);
}

// Непрозрачность космических стилей: ползунок Alpha растягивается (0.4 -> ~0.9), блок почти сплошной.
float opacity() {
    return clamp(Color.a * 2.2, 0.15, 1.0);
}

// Туманность: noise даёт «облака», smoothstep делает у них мягкий край. Координаты медленно дрейфуют по времени.
// На HIGH добавляется domain warping: координаты сначала смещаются другим шумом, получаются «завитки».
float cloudField(vec3 p, float t, float scale, vec3 drift) {
    vec3 q = p * scale + drift * t;
    if (Params.z > 1.5) {
        q += 0.6 * vec3(vnoise(q * 1.3 + 3.7), vnoise(q * 1.3 + 8.2), vnoise(q * 1.3 + 1.1)) - 0.3;
    }
    return fbm(q, octaves());
}

// Слой звёзд на гранях. uv (в блоках) делится на ячейки; в каждой ячейке хэш решает, есть ли звезда (вероятность =
// density), где она (джиттер) и какая у неё фаза. Яркость = гауссово пятно exp(-r^2/s^2) + слабое гало.
// life = fract(time*k + h) - у каждой звезды своя «жизнь»: плавно загорается, горит, гаснет (хаотично появляются и
// пропадают); tw - быстрое мерцание.
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
    float size = 0.05 + 0.05 * h2;
    float core = exp(-(r * r) / (size * size));
    float halo = exp(-r * 3.5) * 0.15;
    return (core + halo) * fade * tw;
}

// Все звёзды: плотность из настройки (вероятность звезды в ячейке 12% * starDensity); на HIGH второй, более мелкий слой.
float stars(vec2 uv, int face, float time) {
    float density = clamp(0.12 * Params.w, 0.0, 0.9);
    float seed = float(face) * 17.0;
    float s = starLayer(uv, 8.0, seed, density, time);
    if (Params.z > 0.5) {
        s += 0.7 * starLayer(uv + 3.17, 17.0, seed + 41.0, density * 0.9, time * 1.3);
    }
    return s;
}

// Градиент туманности: смесь двух цветов вдоль медленно бегущей волны, возмущённой шумом.
vec3 nebulaColor(vec3 p, float t, out float body) {
    float g = cloudField(p, t, 1.6, vec3(0.07, 0.05, -0.06));
    float wave = 0.5 + 0.5 * sin(dot(p, vec3(1.0, 0.7, 0.4)) * 3.0 + t * 0.6 + g * 3.0);
    vec3 col = mix(Color.rgb, ColorB.rgb, wave);
    col *= 0.60 + 0.85 * g;               // яркие и тёмные области
    col += 0.25 * pow(g, 3.0) * vec3(1.0); // светлые «ядра» облаков
    body = g;
    return col;
}

void main() {
    int axis; float edge; vec2 uv;
    faceInfo(axis, edge, uv);
    float em = edgeMask(edge, Params.x * 0.012);
    if (Params.y > 0.5 && em < 0.01) {
        discard;
    }
    float t = BoxMin.w;
    int face = gl_PrimitiveID / 2;
    // GALAXY: туманность (NEBULA) на тёмном космосе + звёзды (STARFIELD). Тёмные просветы между облаками.
    float body;
    vec3 neb = nebulaColor(vBlock, t, body);
    float mask = smoothstep(0.30, 0.75, body);
    vec3 rgb = mix(vec3(0.01, 0.006, 0.025), neb, 0.30 + 0.70 * mask);
    rgb += mix(vec3(1.0), ColorB.rgb, 0.25) * stars(uv, face, t);
    // ребро блока подсвечивается, при «только обводка» остаётся только оно
    vec3 rim = mix(Color.rgb, vec3(1.0), 0.5);
    rgb = mix(rgb, rim, em * 0.8);
    float a = Params.y > 0.5 ? em * min(1.0, opacity() + 0.2) : opacity();
    finish(rgb, a);
}
