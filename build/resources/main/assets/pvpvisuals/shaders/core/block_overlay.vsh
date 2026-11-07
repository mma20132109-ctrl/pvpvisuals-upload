#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// Единичный куб 0..1. Грань определяется во фрагментном шейдере по gl_PrimitiveID / 2
// (порядок граней: -X, +X, -Y, +Y, -Z, +Z).
in vec3 Position;

// Один и тот же блок во всех шейдерах Block Overlay (раскладка std140 должна совпадать).
layout(std140) uniform EchoBlockData {
    vec4 Offset; // xyz: угол блока (min) относительно камеры, w: вынос граней наружу (блоки)
    vec4 BoxMin; // xyz: нижняя граница формы блока в ячейке 0..1, w: время анимации (секунды * скорость)
    vec4 BoxMax; // xyz: верхняя граница формы блока
    vec4 Color;  // rgb: цвет, a: прозрачность
    vec4 Params; // x: толщина линий (1 ~ 0.01 блока), y: только обводка 0/1, z: качество 0/1/2, w: плотность звёзд
    vec4 ColorB; // rgb: второй цвет (космические стили)
};

out vec3 vBlock; // позиция внутри блока, в блоках
out vec3 vRel;   // позиция относительно камеры

void main() {
    // небольшой вынос граней наружу (растёт с расстоянием), чтобы не было z-fighting с самим блоком
    float o = Offset.w + length(Offset.xyz) * 0.0005;
    vec3 mn = BoxMin.xyz - o;
    vec3 mx = BoxMax.xyz + o;
    vBlock = mix(mn, mx, Position);
    vRel = Offset.xyz + vBlock;
    // ModelViewMat = видовая матрица мира (SPEC: BlockOverlayRenderer пишет её в DynamicTransforms),
    // ProjMat = проекция игры, поэтому оверлей двигается вместе с миром, включая покачивание камеры.
    gl_Position = ProjMat * ModelViewMat * vec4(vRel, 1.0);
}
