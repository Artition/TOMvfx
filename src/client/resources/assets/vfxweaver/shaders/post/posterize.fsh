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
    // Number of quantization levels: 255 at strength 0 (no effect) down to 2 at strength 1.
    float levels = max(2.0, 255.0 * pow(1.0 - t, 6.0));
    // Clean quantization to fewer colours. No per-pixel dithering here: scaling the dither by
    // 1/levels at low levels produced large random colour steps (the "pixelation" the old shader
    // showed at high strength). The banding from pure quantization is the intended result.
    vec3 quantized = floor(color.rgb * levels + 0.5) / levels;
    fragColor = vec4(quantized, color.a);
}
