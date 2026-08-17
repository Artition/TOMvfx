#version 330

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform Config {
    float radius;
};

out vec4 fragColor;

#define MAX_SAMPLES 12

void main() {
    float r = max(radius, 0.0);
    float sigma2 = max(2.0 * r * r, 1.0e-3);
    vec2 offset = vec2(0.0, r / InSize.y);

    // Adapt sample count to the blur radius while keeping a fixed upper bound.
    int samples = int(clamp(r * 0.5 + 2.0, 1.0, float(MAX_SAMPLES)));

    vec4 color = texture(InSampler, texCoord);
    float total = 1.0;
    for (int i = 1; i <= MAX_SAMPLES; i++) {
        if (i > samples) {
            break;
        }
        float w = exp(-float(i * i) / sigma2);
        color += texture(InSampler, texCoord + offset * float(i)) * w;
        color += texture(InSampler, texCoord - offset * float(i)) * w;
        total += 2.0 * w;
    }
    fragColor = color / total;
}
