#version 330

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform Config {
    float amount;
    float radius;
};

out vec4 fragColor;

void main() {
    vec2 centered = texCoord - vec2(0.5);
    float d = length(centered);
    float edge = 0.7071068;

    // Smooth bump: full effect near center, fading to zero at the screen edge.
    float falloff = smoothstep(0.0, 1.0, d / edge) * (1.0 - smoothstep(edge * 0.65, edge, d));

    // amount > 0  -> barrel distortion (UVs pushed outward)
    // amount < 0  -> pincushion distortion (UVs pulled inward)
    float k = amount * max(radius, 0.0) * falloff;

    vec2 uv = vec2(0.5) + centered * (1.0 + k * d);

    // Blend back to the original UV near the screen edge to avoid hard clamping artifacts.
    float edgeFade = 1.0 - smoothstep(edge * 0.7, edge, d);
    uv = mix(texCoord, uv, edgeFade);

    fragColor = texture(InSampler, uv);
}
