#version 330

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform Config {
    float radius;
    float direction_x;
    float direction_y;
};

out vec4 fragColor;

const int SAMPLES = 12;

void main() {
    vec2 dir = normalize(vec2(direction_x, direction_y) + vec2(1.0e-5)) / InSize;
    float sigma2 = 2.0 * radius * radius;
    vec4 color = vec4(0.0);
    float total = 0.0;
    for (int i = -SAMPLES; i <= SAMPLES; i++) {
        float w = exp(-float(i * i) / max(sigma2, 1.0e-3));
        color += texture(InSampler, texCoord + dir * float(i) * radius) * w;
        total += w;
    }
    fragColor = color / total;
}
