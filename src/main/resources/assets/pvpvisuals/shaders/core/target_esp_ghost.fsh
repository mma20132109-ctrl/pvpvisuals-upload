#version 330

#moj_import <minecraft:dynamictransforms.glsl>

in vec2 ghostUv;
in vec4 ghostColor;
out vec4 fragColor;

void main() {
    // Soft color and alpha come from the tapered 3D tube mesh.
    // Do not apply a sprite mask: the wisp mesh already has rounded, fading ends.
    float sheen = 0.97 + 0.03 * cos(ghostUv.x * 6.2831853);
    vec3 color = ghostColor.rgb * sheen * ColorModulator.rgb;
    float alpha = ghostColor.a * ColorModulator.a;
    if (alpha < 0.001) {
        discard;
    }
    fragColor = vec4(color, alpha);
}
