#version 330

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in vec2 ghostUv;
in vec4 ghostColor;
out vec4 fragColor;

void main() {
    vec4 sprite = texture(Sampler0, ghostUv);
    vec4 color = sprite * ghostColor * ColorModulator;
    if (color.a < 0.003) {
        discard;
    }
    fragColor = color;
}
