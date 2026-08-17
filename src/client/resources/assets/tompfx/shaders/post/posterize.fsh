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
    float t = clamp(strength, 0.0, 1.0);
    float levels = max(2.0, 255.0 * pow(1.0 - t, 6.0));
    vec2 pixel = floor(texCoord * InSize);
    float hash = fract(sin(dot(pixel, vec2(12.9898, 78.233))) * 43758.5453);
    float noise = (hash - 0.5) / levels;
    vec3 dithered = floor(color.rgb * levels + 0.5 + noise) / levels;
    fragColor = vec4(dithered, color.a);
}
