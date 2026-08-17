#version 330

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform Config {
    float saturation;
    float contrast;
    float brightness;
    float tint_r;
    float tint_g;
    float tint_b;
};

out vec4 fragColor;

void main() {
    vec4 color = texture(InSampler, texCoord);
    float luma = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));
    vec3 graded = mix(vec3(luma), color.rgb, saturation);
    graded = (graded - 0.5) * contrast + 0.5;
    graded = graded * brightness;
    graded *= vec3(tint_r, tint_g, tint_b);
    fragColor = vec4(clamp(graded, 0.0, 1.0), color.a);
}
