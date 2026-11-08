#version 330

#moj_import <minecraft:dynamictransforms.glsl>

in vec2 ghostUv;
in vec4 ghostColor;
out vec4 fragColor;

void main() {
    // The spirit is fully modelled in 3D. No billboard mask or hard-edged discard is used.
    // Vertex colours already contain diffuse and rim lighting from the volumetric mesh.
    float subtleSheen = 0.96 + 0.04 * cos(ghostUv.x * 6.2831853 + ghostUv.y * 3.1415926);
    vec3 color = ghostColor.rgb * subtleSheen * ColorModulator.rgb;
    float alpha = ghostColor.a * ColorModulator.a;
    fragColor = vec4(color, alpha);
}
