#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    // The entity texture acts as an alpha mask: transparent texels are dropped so the effect
    // follows the silhouette of the texture (vanilla rendertype_outline behaviour).
    vec4 tex = texture(Sampler0, texCoord0);
    if (tex.a <= 0.0) {
        discard;
    }

#ifdef OUTLINE
    // Inverted-hull outline: keep only back-facing fragments of the inflated shell so the
    // entity's own surface clips the interior and only the rim around the silhouette remains.
    if (gl_FrontFacing) {
        discard;
    }
    vec4 color = vec4(vertexColor.rgb, vertexColor.a);
#elif defined(TINT_MULTIPLY)
    // Recolour mode: multiply the texture by the effect colour, keeping the texture's own alpha.
    vec4 color = vec4(tex.rgb * vertexColor.rgb, tex.a * vertexColor.a);
#else
    // TINT_MASK (vanilla-outline style): flat fill colour, texture used only as the alpha mask.
    vec4 color = vec4(vertexColor.rgb, vertexColor.a);
#endif

    fragColor = apply_fog(color * ColorModulator, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}