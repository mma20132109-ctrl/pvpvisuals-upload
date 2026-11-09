#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

out vec2 texCoord0;
out vec3 scanColor;
out vec3 secondScanColor;
out float scanHeight;
out float scanPhase;
out float appear;
out float glowStrength;
out float horizontalPosition;
out float scanDirection;
out vec3 modelNormal;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord0 = UV0;
    scanColor = Color.rgb;
    scanHeight = Color.a;

    int packedPhase = UV1.x & 65535;
    int packedMotion = UV1.y & 65535;
    int packedGlow = UV2.x & 65535;
    secondScanColor = vec3(
            float((packedPhase >> 8) & 255),
            float((packedMotion >> 8) & 255),
            float((packedGlow >> 8) & 255)) / 255.0;
    scanPhase = float(packedPhase & 255) / 255.0;

    int motionValue = packedMotion & 255;
    scanDirection = (motionValue & 128) == 0 ? 1.0 : -1.0;
    appear = float(motionValue & 127) / 127.0;

    glowStrength = float(packedGlow & 255) / 127.5;
    horizontalPosition = clamp(float(UV2.y) / 1000.0, 0.0, 1.0);
    modelNormal = normalize(Normal);
}
