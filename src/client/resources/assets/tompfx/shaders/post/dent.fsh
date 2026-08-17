#version 330

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform Config {
    float strength;
    float radius;
    float center_x;
    float center_y;
};

out vec4 fragColor;

void main() {
    vec2 center = vec2(center_x, center_y);
    float aspect = OutSize.x / max(OutSize.y, 1.0);
    vec2 d = vec2((texCoord.x - center.x) * aspect, texCoord.y - center.y);
    float dist = length(d);
    float r = max(radius, 1.0e-4);

    if (dist >= r || strength == 0.0) {
        fragColor = texture(InSampler, texCoord);
        return;
    }

    // Strongest at the point itself, smoothly reaching zero at the edge of the circle.
    float falloff = 1.0 - dist / r;
    falloff *= falloff;

    // strength > 0 shrinks the offset (pixels pulled INTO the point = dent),
    // strength < 0 grows it (pixels pushed OUT of the point = bulge).
    float scale = 1.0 - strength * falloff;

    vec2 shifted = vec2(d.x * scale / aspect, d.y * scale);
    vec2 uv = center + shifted;

    // Blend back to the original UV near the radius to hide any clamping.
    float edgeFade = 1.0 - smoothstep(0.7, 1.0, dist / r);
    uv = mix(texCoord, uv, edgeFade);

    fragColor = texture(InSampler, uv);
}
