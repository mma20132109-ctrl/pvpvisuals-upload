#version 330

// Один полноэкранный треугольник: вершины заданы сразу в clip-пространстве (-1..3), матрицы не нужны.
in vec3 Position;

out vec2 vUv;

void main() {
    vUv = Position.xy * 0.5 + 0.5;
    gl_Position = vec4(Position.xy, 0.0, 1.0);
}
