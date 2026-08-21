#version 330

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform Config {
    float from_r;
    float from_g;
    float from_b;
    float to_r;
    float to_g;
    float to_b;
    float intensity;
    float mode;
    float pos;
};

out vec4 fragColor;

void main() {
    vec4 color = texture(InSampler, texCoord);
    float luma = dot(color.rgb, vec3(0.299, 0.587, 0.114));
    vec3 from = vec3(from_r, from_g, from_b);
    vec3 to = vec3(to_r, to_g, to_b);

    float t;
    if (mode >= 0.5) {
        // Stepped (constant): hard threshold at pos. Below pos -> from, above -> to.
        t = step(pos, luma);
    } else {
        // Linear: smooth gradient, pos shifts the transition centre (0.5 = no shift).
        t = clamp(luma + (pos - 0.5), 0.0, 1.0);
    }

    vec3 mapped = mix(from, to, t);
    fragColor = vec4(mix(color.rgb, mapped, intensity), color.a);
}
