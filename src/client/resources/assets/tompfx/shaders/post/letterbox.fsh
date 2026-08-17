#version 330

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform Config {
    float height;
    float color_r;
    float color_g;
    float color_b;
};

out vec4 fragColor;

void main() {
    float h = clamp(height, 0.0, 0.5);
    if (texCoord.y < h || texCoord.y > 1.0 - h) {
        fragColor = vec4(clamp(color_r, 0.0, 1.0), clamp(color_g, 0.0, 1.0), clamp(color_b, 0.0, 1.0), 1.0);
        return;
    }
    fragColor = texture(InSampler, texCoord);
}
