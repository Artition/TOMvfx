#version 330

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform Config {
    float intensity;
    float size;
    float time;
};

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

void main() {
    vec4 base = texture(InSampler, texCoord);
    // Animated per-frame noise: the grain cell pattern is offset every tick via `time`
    // (the effect's age in ticks), so the grain flickers instead of standing still.
    vec2 cell = texCoord * OutSize / max(size, 1.0);
    vec2 jitter = vec2(fract(time * 0.7317), fract(time * 0.3943));
    float noise = hash(floor(cell) + jitter * 913.0);
    fragColor = vec4(base.rgb + (noise - 0.5) * clamp(intensity, 0.0, 1.0), base.a);
}
