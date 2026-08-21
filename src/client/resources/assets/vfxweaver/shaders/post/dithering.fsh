#version 330

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform Config {
    float strength;
};

out vec4 fragColor;

void main() {
    vec4 color = texture(InSampler, texCoord);
    float levels = max(1.0, strength * 255.0);
    float noise = (fract(sin(dot(floor(texCoord * InSize), vec2(12.9898, 78.233))) * 43758.5453) - 0.5) / levels;
    vec3 dithered = floor(color.rgb * levels + 0.5 + noise) / levels;
    fragColor = vec4(dithered, color.a);
}
