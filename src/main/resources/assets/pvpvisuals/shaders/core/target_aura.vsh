#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec2 UV0;
in vec4 Color;

out vec2 uv;
out vec4 vColor;
out float vFade;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    uv = UV0;
    vColor = Color;
    // depth-based fade: объекты ближе к центру камеры ярче
    float dist = length((ModelViewMat * vec4(Position, 1.0)).xyz);
    vFade = clamp(1.0 - dist * 0.012, 0.0, 1.0);
}
