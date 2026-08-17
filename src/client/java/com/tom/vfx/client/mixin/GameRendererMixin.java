package com.tom.vfx.client.mixin;

import com.tom.vfx.client.effect.VFXEffectManager;
import com.tom.vfx.client.postprocessing.VFXPostProcessingManager;
import com.tom.vfx.effect.VFXWorldBindings;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drives the effect clock and applies the post-processing chain right before the game GUI is
 * drawn (i.e. after the world and the vanilla post chain have been rendered into the main target,
 * but before the overlays that should stay unaffected).
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Inject(
		method = "render(Lnet/minecraft/client/DeltaTracker;Z)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/fog/FogRenderer;endFrame()V",
			shift = At.Shift.BEFORE
		)
	)
	private void tompfx$render(final DeltaTracker deltaTracker, final boolean advanceGameTime, final CallbackInfo ci) {
		Minecraft minecraft = Minecraft.getInstance();
		VFXEffectManager manager = VFXEffectManager.get();
		if (minecraft.level == null) {
			manager.stopAll();
			VFXWorldBindings.clear();
			return;
		}

		float deltaTicks = minecraft.isPaused() ? 0.0F : deltaTracker.getGameTimeDeltaTicks();

		Camera camera = minecraft.gameRenderer.getMainCamera();
		if (camera.isInitialized()) {
			Vec3 camPos = camera.position();
			Matrix4f viewRotProj = camera.getViewRotationProjectionMatrix(new Matrix4f());
			VFXWorldBindings.update((float) camPos.x, (float) camPos.y, (float) camPos.z, camera.yRot(), camera.xRot(), viewRotProj, deltaTicks);
		}
		manager.advance(deltaTicks);
		manager.update();

		VFXPostProcessingManager.get().process(manager, minecraft.getMainRenderTarget());
	}
}
