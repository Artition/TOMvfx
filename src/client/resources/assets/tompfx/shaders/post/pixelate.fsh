#version 330

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform Config {
    float cell_size;
};

out vec4 fragColor;

void main() {
    vec2 cell = max(vec2(cell_size), vec2(1.0e-4)) * InSize;
    vec2 uv = (floor(texCoord * InSize / cell) * cell + cell * 0.5) / InSize;
    fragColor = texture(InSampler, uv);
}
