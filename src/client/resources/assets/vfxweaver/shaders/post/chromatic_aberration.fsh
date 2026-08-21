#version 330

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform Config {
    float intensity;
    float radius;
};

out vec4 fragColor;

void main() {
    vec2 dir = texCoord - vec2(0.5);
    float dist = length(dir);
    vec2 ndir = normalize(dir + vec2(1.0e-5));
    float spread = max(intensity, 0.0) * max(radius, 0.0) * (0.4 + dist);
    fragColor = vec4(
        texture(InSampler, texCoord + ndir * spread / InSize).r,
        texture(InSampler, texCoord).g,
        texture(InSampler, texCoord - ndir * spread / InSize).b,
        1.0
    );
}
