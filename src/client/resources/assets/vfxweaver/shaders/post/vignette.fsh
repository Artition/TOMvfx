#version 330

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform Config {
    float intensity;
    float color_r;
    float color_g;
    float color_b;
};

out vec4 fragColor;

void main() {
    vec4 color = texture(InSampler, texCoord);
    vec2 center = texCoord - vec2(0.5);
    float dist = length(center) * 1.41421356; // normalize to 0..1 at corners
    float t = clamp(intensity, 0.0, 1.0) * smoothstep(0.0, 1.0, dist);
    vec3 vignetteColor = vec3(clamp(color_r, 0.0, 1.0), clamp(color_g, 0.0, 1.0), clamp(color_b, 0.0, 1.0));
    fragColor = vec4(mix(color.rgb, vignetteColor, t), color.a);
}
