#version 330

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform Config {
    float intensity;
    float focus_center;
    float focus_range;
};

out vec4 fragColor;

void main() {
    // ponytail: screen-space "tilt-shift" DoF (vertical focus band), not depth-based —
    // the post chain has no access to the depth buffer.
    float d = abs(texCoord.y - clamp(focus_center, 0.0, 1.0));
    float t = smoothstep(max(focus_range, 0.0), max(focus_range, 0.0) * 2.0 + 0.15, d);
    float blurPx = clamp(intensity, 0.0, 1.0) * t * 12.0;

    vec4 base = texture(InSampler, texCoord);
    if (blurPx < 0.5) {
        fragColor = base;
        return;
    }

    vec2 texel = vec2(blurPx / max(InSize.x, 1.0), blurPx / max(InSize.y, 1.0));
    vec4 color = base;
    float total = 1.0;
    for (int i = 0; i < 8; i++) {
        float angle = 6.2831853 * float(i) / 8.0;
        vec2 offset = vec2(cos(angle), sin(angle)) * texel;
        color += texture(InSampler, texCoord + offset);
        total += 1.0;
    }
    fragColor = color / total;
}
