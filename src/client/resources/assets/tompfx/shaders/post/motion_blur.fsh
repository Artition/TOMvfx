#version 330

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform Config {
    float intensity;
    float yaw_delta;
    float pitch_delta;
};

out vec4 fragColor;

#define MAX_SAMPLES 12

void main() {
    vec2 dir = vec2(yaw_delta, pitch_delta) * clamp(intensity, 0.0, 1.0);
    float len = length(dir);
    // Scale the direction so the blur stays visible but not overwhelming.
    vec2 offset = len < 1.0e-4 ? vec2(0.0) : normalize(dir) * (len * 16.0) / InSize;

    int samples = min(MAX_SAMPLES, max(2, int(len * 1.5) + 2));

    vec4 color = texture(InSampler, texCoord);
    float total = 1.0;
    for (int i = 1; i <= MAX_SAMPLES; i++) {
        if (i > samples) {
            break;
        }
        float w = float(samples - i + 1);
        color += texture(InSampler, texCoord + offset * float(i)) * w;
        total += w;
    }
    fragColor = color / total;
}
