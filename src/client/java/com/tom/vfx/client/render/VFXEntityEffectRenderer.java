package com.tom.vfx.client.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.tom.vfx.effect.VFXActiveEffect;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Second-pass renderer for {@code entity_tint} and {@code entity_outline} effects. Both effects
 * re-submit the entity's own model (same {@link DefaultVertexFormat.ENTITY} vertices) through a
 * custom {@link RenderType}, so the geometry is rendered a second time without touching the
 * vanilla textures or shaders.
 *
 * <p><b>Tint</b> is a solid-colour translucent fill: the effect ARGB is passed as the model tint,
 * the fragment shader ignores textures/lighting and outputs the vertex colour. Depth is
 * {@code LEQUAL} by default ({@code through_blocks = 0}) or {@code ALWAYS_PASS} when the effect
 * should be visible through terrain.</p>
 *
 * <p><b>Outline</b> is an inverted hull: the model is re-submitted scaled around its vertical
 * centre and only back-facing fragments are kept (front faces are discarded in the fragment
 * shader — the pipeline API only offers back-face culling, no front-face mode). With a
 * {@code LEQUAL} depth test the inflated shell stays behind the entity's own surface, leaving a
 * clean rim around the silhouette; with {@code ALWAYS_PASS} it becomes a see-through glow.
 * The {@code width} parameter scales the silhouette (uniform scale around the model centre), not
 * a per-vertex normal offset: a per-draw width uniform would need a custom UBO that the
 * {@code submitModel} draw path cannot bind.
 * // ponytail: width is scale-around-centre (thickness varies with distance from centre), not a
 * // constant world-space offset; switch to a normal-offset shader with a per-draw UBO if constant
 * // thickness is ever needed.
 */
public final class VFXEntityEffectRenderer {
	private static RenderPipeline entityFxPipeline(final String suffix, final CompareOp depthOp, final boolean outline) {
		RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_LIGHT_DIR_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath("tompfx", "world/entity_" + suffix))
			.withVertexShader(Identifier.fromNamespaceAndPath("tompfx", "core/entity_fx"))
			.withFragmentShader(Identifier.fromNamespaceAndPath("tompfx", "core/entity_fx"))
			.withVertexFormat(DefaultVertexFormat.ENTITY, VertexFormat.Mode.QUADS)
			.withDepthStencilState(new DepthStencilState(depthOp, false))
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.withCull(false);
		if (outline) {
			builder.withShaderDefine("OUTLINE");
		}
		return RenderPipelines.register(builder.build());
	}

	private static final RenderType TINT_VISIBLE = RenderType.create(
		"tompfx_entity_tint_visible",
		RenderSetup.builder(entityFxPipeline("tint_visible", CompareOp.ALWAYS_PASS, false)).createRenderSetup()
	);

	private static final RenderType TINT_OCCLUDED = RenderType.create(
		"tompfx_entity_tint_occluded",
		RenderSetup.builder(entityFxPipeline("tint_occluded", CompareOp.LESS_THAN_OR_EQUAL, false)).createRenderSetup()
	);

	private static final RenderType OUTLINE_VISIBLE = RenderType.create(
		"tompfx_entity_outline_visible",
		RenderSetup.builder(entityFxPipeline("outline_visible", CompareOp.ALWAYS_PASS, true)).createRenderSetup()
	);

	private static final RenderType OUTLINE_OCCLUDED = RenderType.create(
		"tompfx_entity_outline_occluded",
		RenderSetup.builder(entityFxPipeline("outline_occluded", CompareOp.LESS_THAN_OR_EQUAL, true)).createRenderSetup()
	);

	private VFXEntityEffectRenderer() {
	}

	/**
	 * Forces class initialisation (pipeline registration) at client startup, before the first
	 * resource reload precompiles the shaders. Idempotent.
	 */
	public static void register() {
		RenderType tint = TINT_VISIBLE;
		RenderType tintOccluded = TINT_OCCLUDED;
		RenderType outline = OUTLINE_VISIBLE;
		RenderType outlineOccluded = OUTLINE_OCCLUDED;
	}

	private static <S extends LivingEntityRenderState> void submit(
		final S state,
		final PoseStack poseStack,
		final SubmitNodeCollector submitNodeCollector,
		final Model<? super S> model,
		final RenderType renderType,
		final int color
	) {
		submitNodeCollector.submitModel(model, state, poseStack, renderType, state.lightCoords, OverlayTexture.NO_OVERLAY, color, null, 0, null);
	}

	/**
	 * Solid-colour tint pass: re-submits the model with the effect ARGB as the model tint.
	 */
	public static <S extends LivingEntityRenderState> void renderTint(
		final VFXActiveEffect effect,
		final S state,
		final PoseStack poseStack,
		final SubmitNodeCollector submitNodeCollector,
		final Model<? super S> model
	) {
		float alpha = clamp01(effect.getParam("alpha", 0.5F)) * effect.getWeight();
		if (alpha <= 0.0F) {
			return;
		}
		boolean through = effect.getParam("through_blocks", 1.0F) >= 0.5F;
		submit(state, poseStack, submitNodeCollector, model, through ? TINT_VISIBLE : TINT_OCCLUDED, argb(effect, alpha));
	}

	/**
	 * Inverted-hull outline pass: re-submits the model scaled around its vertical centre and lets
	 * the shader keep only back-facing fragments, producing a rim around the entity silhouette.
	 */
	public static <S extends LivingEntityRenderState> void renderOutline(
		final VFXActiveEffect effect,
		final S state,
		final PoseStack poseStack,
		final SubmitNodeCollector submitNodeCollector,
		final Model<? super S> model
	) {
		float alpha = clamp01(effect.getParam("alpha", 1.0F)) * effect.getWeight();
		if (alpha <= 0.0F) {
			return;
		}
		boolean through = effect.getParam("through_blocks", 0.0F) >= 0.5F;
		float width = Mth.clamp(effect.getParam("width", 0.05F), 0.0F, 1.0F);
		int color = argb(effect, alpha);

		poseStack.pushPose();
		try {
			// Model space has +Y pointing down and the origin at the feet, so the vertical centre
			// sits at -boundingBoxHeight/2. Scaling around it keeps the outline centred on the body.
			float pivot = state.boundingBoxHeight / 2.0F;
			poseStack.translate(0.0F, -pivot, 0.0F);
			poseStack.scale(1.0F + width, 1.0F + width, 1.0F + width);
			poseStack.translate(0.0F, pivot, 0.0F);
			submit(state, poseStack, submitNodeCollector, model, through ? OUTLINE_VISIBLE : OUTLINE_OCCLUDED, color);
		} finally {
			poseStack.popPose();
		}
	}

	private static int argb(final VFXActiveEffect effect, final float alpha) {
		int a = Mth.clamp((int) (alpha * 255.0F), 0, 255);
		int r = Mth.clamp((int) (clamp01(effect.getParam("color_r", 1.0F)) * 255.0F), 0, 255);
		int g = Mth.clamp((int) (clamp01(effect.getParam("color_g", 1.0F)) * 255.0F), 0, 255);
		int b = Mth.clamp((int) (clamp01(effect.getParam("color_b", 1.0F)) * 255.0F), 0, 255);
		return a << 24 | r << 16 | g << 8 | b;
	}

	private static float clamp01(final float value) {
		return Mth.clamp(value, 0.0F, 1.0F);
	}
}