#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
#ifdef OUTLINE
    // Inverted-hull outline: keep only back-facing fragments of the inflated shell so the
    // entity's own surface clips the interior and only the rim around the silhouette remains.
    if (gl_FrontFacing) {
        discard;
    }
#endif
    vec4 color = vertexColor * ColorModulator;
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}