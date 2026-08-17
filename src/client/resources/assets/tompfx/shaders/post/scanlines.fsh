#version 330

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform Config {
    float intensity;
    float line_count;
    float speed;
    float time;
};

out vec4 fragColor;

void main() {
    vec4 base = texture(InSampler, texCoord);
    // line_count = bands per 100 px of height; the pattern drifts downwards at `speed`
    // screen fractions per tick (negative speed scrolls up).
    float lines = max(line_count, 0.1) * max(OutSize.y, 1.0) / 100.0;
    float phase = (texCoord.y + time * speed * 0.01) * lines * 6.2831853;
    float band = 0.5 + 0.5 * cos(phase);
    float darken = band * clamp(intensity, 0.0, 1.0);
    fragColor = vec4(base.rgb * (1.0 - darken), base.a);
}
