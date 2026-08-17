#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec3 Normal;
in float LineWidth;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;

// Block outline expansion: unlike the vanilla rendertype_lines shader (which expands the
// stroke symmetrically to BOTH sides of the edge, so the inner half is depth-occluded by
// the block itself), this shader expands only OUTWARDS. Normal is the face normal of the
// quad that owns the edge; the stroke covers [edge, edge + LineWidth pixels] along the
// screen-space projection of that normal. Even vertex invocations land at the outer side,
// odd invocations stay on the edge, giving a stroke of exactly LineWidth pixels that hugs
// the block and is fully visible.
void main() {
    vec4 edgePos = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vec4 outerPos = ProjMat * ModelViewMat * vec4(Position + Normal, 1.0);

    vec2 edgeNdc = edgePos.xy / edgePos.w;
    vec2 outerNdc = outerPos.xy / outerPos.w;

    vec2 screenDelta = (outerNdc - edgeNdc) * ScreenSize;
    float len = length(screenDelta);
    vec2 lineOffset = len < 1.0e-4
        ? vec2(0.0)
        : normalize(screenDelta) * (2.0 * LineWidth / ScreenSize);

    if (gl_VertexID % 2 == 0) {
        gl_Position = vec4((edgeNdc + lineOffset) * edgePos.w, edgePos.z, edgePos.w);
    } else {
        gl_Position = vec4(edgeNdc * edgePos.w, edgePos.z, edgePos.w);
    }

    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    vertexColor = Color;
}
