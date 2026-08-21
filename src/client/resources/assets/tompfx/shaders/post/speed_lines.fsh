#version 330

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform Config {
    float center_x;
    float center_y;
    float count;
    float length;
    float width;
    float seed;
    float color_r;
    float color_g;
    float color_b;
    float intensity;
};

out vec4 fragColor;

void main() {
    vec4 texColor = texture(InSampler, texCoord);

    // UV relative to the effect centre, corrected for aspect so the lines stay circular.
    float aspect = max(OutSize.x / max(OutSize.y, 1.0e-4), 1.0e-4);
    vec2 centeredUV = vec2(texCoord.x - center_x, texCoord.y - center_y);
    centeredUV.x *= aspect;

    float dist = length(centeredUV);
    float angle = atan(centeredUV.y, centeredUV.x);

    // Normalise the angle to [0, 1] and split the screen into sectors.
    float normAngle = (angle / 6.2831853) + 0.5;
    float sectors = max(count, 1.0);
    float segmentSize = 1.0 / sectors;
    float segIndex = floor(normAngle / segmentSize);

    // Per-sector pseudo-random value, seeded by the (possibly animated) seed.
    float rand = fract(sin(segIndex * 12.9898 + seed * 78.233) * 43758.5453);

    // Line width mask inside the sector.
    float localAngle = (normAngle - segIndex * segmentSize) / segmentSize;
    float w = clamp(width, 0.0, 1.0);
    float widthMask = 1.0 - smoothstep(w * 0.5, w * 0.5 + 0.01, abs(localAngle - 0.5) * 2.0);

    // Length mask: lines start a bit away from the centre and end at start + length.
    float startRadius = 0.1;
    float endRadius = startRadius + clamp(length, 0.0, 1.0);
    float distMask = smoothstep(startRadius, startRadius + 0.02, dist)
        * (1.0 - smoothstep(endRadius - 0.05, endRadius, dist));

    float lineMask = widthMask * distMask;

    vec3 lineColor = vec3(clamp(color_r, 0.0, 1.0), clamp(color_g, 0.0, 1.0), clamp(color_b, 0.0, 1.0));
    float a = clamp(intensity, 0.0, 1.0) * lineMask;
    fragColor = vec4(mix(texColor.rgb, lineColor, a), texColor.a);
}
