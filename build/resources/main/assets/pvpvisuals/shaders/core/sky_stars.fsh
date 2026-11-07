#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

in vec3 rayDir;
out vec4 fragColor;

// std140-блок, заполняется в SkyRenderer.draw():
//   A = (время в секундах * speed, яркость, высота солнца -1..1, измерение 0/1/2)
//   B = (timeOfDayLock 0/1, 0, 0, 0)
//   C = (tan(fovY/2), aspect, 0, 0) - нужны только вершинному шейдеру
//   D = (оттенок 0..1, насыщенность, свой цвет 0/1, 0)
layout(std140) uniform EchoSkyData {
    vec4 A;
    vec4 B;
    vec4 C;
    vec4 D;
};

#define TAU 6.28318530718

// Выбор цвета по измерению: обычный мир / Незер / Энд.
vec3 byDim(vec3 overworld, vec3 nether, vec3 end) {
    int dim = int(A.w + 0.5);
    return dim == 1 ? nether : (dim == 2 ? end : overworld);
}

// Дешёвый хэш (Dave Hoskins, "Hash without Sine"): псевдослучайные числа из координат без sin().
float hash13(vec3 p3) {
    p3 = fract(p3 * 0.1031);
    p3 += dot(p3, p3.zyx + 31.32);
    return fract((p3.x + p3.y) * p3.z);
}

vec3 hash33(vec3 p3) {
    p3 = fract(p3 * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yxz + 33.33);
    return fract((p3.xxy + p3.yxx) * p3.zyx);
}

// Value noise в 3D: хэши в 8 углах ячейки, трилинейная интерполяция со сглаживанием f*f*(3-2f).
float vnoise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float n000 = hash13(i);
    float n100 = hash13(i + vec3(1, 0, 0));
    float n010 = hash13(i + vec3(0, 1, 0));
    float n110 = hash13(i + vec3(1, 1, 0));
    float n001 = hash13(i + vec3(0, 0, 1));
    float n101 = hash13(i + vec3(1, 0, 1));
    float n011 = hash13(i + vec3(0, 1, 1));
    float n111 = hash13(i + vec3(1, 1, 1));
    return mix(mix(mix(n000, n100, f.x), mix(n010, n110, f.x), f.y),
               mix(mix(n001, n101, f.x), mix(n011, n111, f.x), f.y), f.z);
}

// Fractal Brownian motion: сумма октав шума, каждая вдвое мельче и слабее.
float fbm(vec3 p) {
    float sum = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 5; i++) {
        sum += amp * vnoise(p);
        p = p * 2.03 + vec3(17.1, 3.7, 9.2);
        amp *= 0.5;
    }
    return sum;
}

// Итоговый цвет: яркость, лёгкий дизеринг против полос на тёмных градиентах.
// Поворот цвета вокруг серой оси (1,1,1) на угол a (формула Родрига): меняет оттенок, почти не трогая яркость.
vec3 hueRotate(vec3 c, float a) {
    vec3 k = vec3(0.57735027);
    return c * cos(a) + cross(k, c) * sin(a) + k * dot(k, c) * (1.0 - cos(a));
}

vec4 finish(vec3 col) {
    // Свой цвет: палитры шейдеров фиолетовые (оттенок ~280 градусов), поэтому поворачиваем оттенок на
    // разницу между выбранным и фиолетовым, затем масштабируем насыщенность.
    if (D.z > 0.5) {
        col = max(hueRotate(col, (D.x - 0.77777778) * TAU), 0.0);
        float luma = dot(col, vec3(0.299, 0.587, 0.114));
        col = mix(vec3(luma), col, D.y);
    }
    float dither = (hash13(vec3(gl_FragCoord.xy, 1.0)) - 0.5) / 255.0;
    return vec4(max(col * A.y + dither, 0.0), 1.0);
}


// ---------- Stars: чёрное небо и мерцающие звёзды (без ванильных кубических звёзд) ----------

// Один слой звёзд. Направление умножается на scale и делится на 3D-ячейки; в каждой ячейке может быть
// одна звезда в случайной точке. Расстояние до неё даёт круглое пятно; чем ближе, тем ярче.
vec3 starLayer(vec3 d, float scale, float density, float size, float t) {
    vec3 p = d * scale;
    vec3 id = floor(p);
    vec3 f = fract(p);
    vec3 rnd = hash33(id);
    float exists = step(1.0 - density, hash13(id + 7.7));          // есть ли звезда в этой ячейке
    vec3 pos = 0.2 + 0.6 * rnd;                                    // её позиция внутри ячейки
    float dist = length(f - pos);
    float spot = 1.0 - smoothstep(0.0, size, dist);                      // круглое пятно со сглаженным краем
    float core = 1.0 - smoothstep(0.0, size * 0.35, dist);               // яркое ядро
    // Мерцание: у каждой звезды своя частота и фаза.
    float tw = 0.65 + 0.35 * sin(t * (0.8 + rnd.x * 2.2) + rnd.y * TAU);
    // Цвет: от голубовато-белого до фиолетового.
    vec3 tint = mix(vec3(0.75, 0.85, 1.0), vec3(0.85, 0.6, 1.0), rnd.z);
    return tint * (spot * 0.8 + core) * tw * exists;
}

void main() {
    vec3 d = normalize(rayDir);
    float t = A.x;

    vec3 sky = byDim(vec3(0.0, 0.0, 0.004), vec3(0.03, 0.0, 0.0), vec3(0.008, 0.0, 0.02));
    vec3 starTint = byDim(vec3(1.0), vec3(1.0, 0.65, 0.5), vec3(0.85, 0.7, 1.0));

    // Энд: слабое искажение направления.
    vec3 p = d;
    if (int(A.w + 0.5) == 2) {
        p += 0.03 * vec3(sin(d.y * 6.0 + t * 0.3), sin(d.z * 5.0 - t * 0.25), sin(d.x * 6.0 + t * 0.28));
    }

    vec3 col = sky;
    col += starLayer(p, 38.0, 0.30, 0.20, t) * starTint;          // крупные редкие
    col += starLayer(p, 80.0, 0.35, 0.16, t * 1.3) * starTint * 0.8; // средние
    col += starLayer(p, 150.0, 0.40, 0.14, t * 0.9) * starTint * 0.55; // мелкая россыпь

    // Едва заметная туманность для глубины.
    col += vec3(0.05, 0.01, 0.09) * smoothstep(0.5, 0.9, fbm(p * 2.0 + vec3(t * 0.01))) * 0.5;

    // Если время суток не заблокировано: днём звёзды гаснут, небо светлеет.
    float day = (B.x > 0.5) ? 0.0 : smoothstep(-0.12, 0.3, A.z);
    col = mix(col, sky + vec3(0.03, 0.02, 0.10), day);

    col *= mix(0.3, 1.0, smoothstep(-0.3, 0.05, d.y));
    fragColor = finish(col);
}
