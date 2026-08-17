#version 330

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform Config {
    float intensity;
    float threshold;
    float radius;
};

out vec4 fragColor;

void main() {
    vec4 base = texture(InSampler, texCoord);
    if (intensity <= 0.0) {
        fragColor = base;
        return;
    }

    // ponytail: single-pass 9-tap glow instead of a true threshold+blur+composite
    // chain; add a multi-pass bloom if large halos are ever needed.
    float r = clamp(radius, 0.0, 16.0);
    vec2 texel = vec2(r / max(InSize.x, 1.0), r / max(InSize.y, 1.0));

    vec3 glow = vec3(0.0);
    float total = 0.0;
    for (int i = 0; i < 8; i++) {
        float angle = 6.2831853 * float(i) / 8.0;
        vec2 offset = vec2(cos(angle), sin(angle)) * texel;
        vec3 c = texture(InSampler, texCoord + offset).rgb;
        float lum = dot(c, vec3(0.2126, 0.7152, 0.0722));
        float w = smoothstep(threshold, threshold + 0.25, lum);
        glow += c * w;
        total += 1.0;
    }

    fragColor = vec4(base.rgb + (glow / max(total, 1.0)) * intensity, base.a);
}
