#version 330

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform Config {
    float intensity;
    float yaw_delta;
    float pitch_delta;
};

out vec4 fragColor;

// A stable, fixed sample count avoids the "stepped" banding of a variable count that
// scaled with the (jumpy) per-frame camera delta.
#define MAX_SAMPLES 9
#define HALF (MAX_SAMPLES / 2)

void main() {
    vec2 dir = vec2(yaw_delta, pitch_delta) * clamp(intensity, 0.0, 1.0);
    float len = length(dir);
    if (len < 1.0e-4) {
        fragColor = texture(InSampler, texCoord);
        return;
    }

    // Blur length in screen pixels: smooth, clamped, scaled by the shorter screen axis so it
    // looks the same at any resolution. The camera delta is smoothed upstream, and clamping
    // the length prevents a single fast frame from exploding the blur.
    float scale = clamp(len * 40.0, 0.0, 52.0) / max(min(InSize.x, InSize.y), 1.0);
    vec2 ndir = dir / len;
    vec2 offset = ndir * scale;

    // Symmetric directional blur: samples on both sides of the pixel, weighted linearly to 0 at
    // the ends. Being centered (no one-sided smear) plus a fixed sample count removes the
    // "torn"/jittery look.
    vec4 color = texture(InSampler, texCoord);
    float total = 1.0;
    for (int i = 1; i <= HALF; i++) {
        float w = 1.0 - float(i) / float(HALF + 1);
        vec2 off = offset * (float(i) / float(HALF));
        color += texture(InSampler, texCoord + off) * w;
        color += texture(InSampler, texCoord - off) * w;
        total += 2.0 * w;
    }
    fragColor = color / total;
}
