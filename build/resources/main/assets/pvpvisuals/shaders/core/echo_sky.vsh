#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// Один большой треугольник на весь экран. Position.xy уже в NDC (-1..3).
in vec3 Position;

// Тот же блок, что и во фрагментных шейдерах (раскладка std140 должна совпадать).
layout(std140) uniform EchoSkyData {
    vec4 A;
    vec4 B;
    vec4 C; // (tan(fovY/2), aspect, 0, 0)
    vec4 D; // (hue, saturation, custom color on/off, 0)
};

// Направление луча в мировом пространстве, интерполируется на фрагменты.
out vec3 rayDir;

void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);

    // Проекцию игры (ProjMat) НЕ используем: в неё домножаются покачивание камеры при ходьбе и тряска
    // от урона, и небо «шаталось» бы вместе с ними. Вместо неё собираем направление взгляда сами из FOV
    // и пропорций экрана: в пространстве вида камера смотрит вдоль -Z.
    vec3 viewDir = vec3(Position.x * C.x * C.y, Position.y * C.x, -1.0);

    // ModelViewMat здесь — чистое вращение камеры (см. SkyRenderer), у вращения обратная матрица = транспонированная.
    rayDir = transpose(mat3(ModelViewMat)) * viewDir;
}
