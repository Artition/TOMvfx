package com.tom.vfx.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tom.vfx.client.effect.VFXEffectManager;
import com.tom.vfx.client.render.VFXEntityEffectRenderer;
import com.tom.vfx.effect.VFXActiveEffect;
import com.tom.vfx.effect.VFXEffectType;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks {@link LivingEntityRenderer} twice:
 *
 * <ul>
 *   <li>after {@code extractRenderState} — stores the entity's UUID on the render state (see
 *       {@link ITomVFXEntityState});</li>
 *   <li>after the original {@code submitModel} in {@code submit} — re-submits the model with the
 *       {@code entity_tint}/{@code entity_outline} render types so the effect renders as a second
 *       pass on top of (or around) the entity's own geometry, without touching the vanilla render
 *       type or its textures.</li>
 * </ul>
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>> {
	private static final Logger LOGGER = LoggerFactory.getLogger("tompfx/entity-fx");

	@Shadow
	protected M model;

	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
	private void tompfx$storeEntityUuid(final LivingEntity entity, final LivingEntityRenderState state, final float partialTicks, final CallbackInfo ci) {
		((ITomVFXEntityState) state).tompfx$setUuid(entity.getUUID());
	}

	@Inject(
		method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V",
			shift = At.Shift.AFTER
		)
	)
	private void tompfx$applyEntityEffects(final S state, final PoseStack poseStack, final SubmitNodeCollector submitNodeCollector, final CameraRenderState camera, final CallbackInfo ci) {
		UUID uuid = ((ITomVFXEntityState) state).tompfx$getUuid();
		if (uuid == null) {
			return;
		}
		List<VFXActiveEffect> effects = VFXEffectManager.get().getActiveEntityEffects(uuid);
		if (effects.isEmpty()) {
			return;
		}
		for (VFXActiveEffect effect : effects) {
			try {
				if (effect.getType() == VFXEffectType.ENTITY_TINT) {
					VFXEntityEffectRenderer.renderTint(effect, state, poseStack, submitNodeCollector, this.model);
				} else if (effect.getType() == VFXEffectType.ENTITY_OUTLINE) {
					VFXEntityEffectRenderer.renderOutline(effect, state, poseStack, submitNodeCollector, this.model);
				}
			} catch (Exception e) {
				LOGGER.warn("Failed to apply entity effect '{}'", effect.getId(), e);
			}
		}
	}
}