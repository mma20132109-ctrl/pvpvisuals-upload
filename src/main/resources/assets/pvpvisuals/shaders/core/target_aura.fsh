#version 330

#moj_import <minecraft:dynamictransforms.glsl>

in vec2 uv;
in vec4 vColor;
in float vFade;
out vec4 fragColor;

void main() {
    // UV.x = нормализованная позиция вдоль дуги (0..1), UV.y = нормализованная ширина трубки (-1..1)
    float t = uv.x;         // позиция вдоль кольца
    float w = uv.y;         // -1..1: край трубки, 0: центр

    // Мягкие края трубки (форма кольца): гауссиан по ширине
    float edgeSoft = exp(-w * w * 5.5);

    // Пульсация вдоль дуги: переливающееся свечение
    float pulse = 0.72 + 0.28 * sin(t * 6.2831853 * 2.0 - uv.x * 12.566);

    // Мерцание по времени: анимация (TimeElapsed нет — используем uvX как proxy для phase diversity)
    float shimmer = 0.88 + 0.12 * cos(t * 31.415);

    float alpha = edgeSoft * pulse * shimmer * vColor.a * ColorModulator.a * vFade;
    if (alpha < 0.004) discard;

    // Лёгкое пересвечивание в центре трубки: bloom-имитация
    float bloom = 1.0 + 0.35 * exp(-w * w * 18.0);
    vec3 color = vColor.rgb * bloom * ColorModulator.rgb;

    fragColor = vec4(clamp(color, 0.0, 1.5), alpha);
}
