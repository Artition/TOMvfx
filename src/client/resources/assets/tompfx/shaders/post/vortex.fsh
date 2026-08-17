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
    // Aspect ratio from SamplerInfo keeps the vortex round on wide screens.
    float aspect = OutSize.x / max(OutSize.y, 1.0);
    vec2 center = vec2(center_x, center_y);
    vec2 offset = vec2((texCoord.x - center.x) * aspect, texCoord.y - center.y);
    float dist = length(offset);
    float r = max(radius, 1.0e-4);

    // Twist is strongest at the center and fades out to the radius edge;
    // negative strength spins the other way.
    float falloff = 1.0 - smoothstep(0.0, r, dist);
    float angle = strength * falloff;

    float s = sin(angle);
    float c = cos(angle);
    mat2 rotMatrix = mat2(c, -s, s, c);
    vec2 rotatedOffset = rotMatrix * offset;
    rotatedOffset.x /= aspect;

    vec2 uv = center + rotatedOffset;
    fragColor = texture(InSampler, uv);
}
