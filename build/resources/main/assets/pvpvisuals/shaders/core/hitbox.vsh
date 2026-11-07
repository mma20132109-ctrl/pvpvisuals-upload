#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// Вершина: позиция относительно камеры + цвет (RGBA, 4 байта). Все хитбоксы идут одним буфером.
in vec3 Position;
in vec4 Color;

out vec4 vertexColor;

void main() {
    // ModelViewMat = видовая матрица мира (HitBoxRenderer пишет её в DynamicTransforms), ProjMat = проекция игры.
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
}
