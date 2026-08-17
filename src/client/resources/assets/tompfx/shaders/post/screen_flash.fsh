#version 330

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform Config {
    float alpha;
    float color_r;
    float color_g;
    float color_b;
};

out vec4 fragColor;

void main() {
    vec4 color = texture(InSampler, texCoord);
    vec3 flashColor = vec3(clamp(color_r, 0.0, 1.0), clamp(color_g, 0.0, 1.0), clamp(color_b, 0.0, 1.0));
    float a = clamp(alpha, 0.0, 1.0);
    fragColor = vec4(mix(color.rgb, flashColor, a), color.a);
}
