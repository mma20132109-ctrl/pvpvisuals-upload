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

void main() {
    int axis; float edge; vec2 uv;
    faceInfo(axis, edge, uv);
    float w = Params.x * 0.01;
    float em = edgeMask(edge, w * 1.5);
    if (Params.y > 0.5 && em < 0.01) {
        discard;
    }
    float a = Params.y > 0.5 ? em * min(1.0, Color.a + 0.45) : Color.a;
    finish(mix(Color.rgb, vec3(1.0), em * 0.3), a);
}
