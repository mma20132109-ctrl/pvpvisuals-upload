#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

in vec4 vertexColor;
out vec4 fragColor;

void main() {
    if (vertexColor.a < 0.004) {
        discard;
    }
    fragColor = vertexColor * ColorModulator;
}
