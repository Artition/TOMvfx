package com.tom.vfx.client.mixin;

import com.tom.vfx.client.effect.VFXEffectManager;
import com.tom.vfx.client.shake.CameraShakeManager;
import com.tom.vfx.client.shake.CameraSmoothingManager;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies the combined camera shake from active {@code camera_shake} effects once the base
 * camera pose has been computed. The offsets are added on top of the real rotation and position
 * so that {@code extractRenderState} copies the shaken pose into the render state.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Shadow
	protected abstract void setPosition(Vec3 position);

	@Shadow
	private Vec3 position;

	@Shadow
	@Final
	private Vector3f forwards;

	@Shadow
	@Final
	private Vector3f up;

	@Shadow
	@Final
	private Vector3f left;

	@Shadow
	@Final
	private Quaternionf rotation;

	@Shadow
	private float xRot;

	@Shadow
	private float yRot;

	@Inject(method = "calculateFov(F)F", at = @At("RETURN"), cancellable = true)
	private void tompfx$modifyFov(final float partialTick, final CallbackInfoReturnable<Float> cir) {
		float delta = VFXEffectManager.get().getActiveFovDelta();
		if (delta != 0.0F) {
			cir.setReturnValue(cir.getReturnValue() + delta);
		}
	}

	@Inject(method = "update(Lnet/minecraft/client/DeltaTracker;)V", at = @At("TAIL"))
	private void tompfx$applyShake(final DeltaTracker deltaTracker, final CallbackInfo ci) {
		// Cinematic smoothing first: applies inertia to the base rotation, then the shake offset
		// is layered on top so the two don't fight each other.
		float deltaSeconds = deltaTracker.getRealtimeDeltaTicks() / 20.0f;
		float[] smoothed = CameraSmoothingManager.smooth(this.yRot, this.xRot, deltaSeconds);
		float baseYaw = smoothed[0];
		float basePitch = smoothed[1];

		CameraShakeManager.Offset offset = CameraShakeManager.compute(VFXEffectManager.get());
		if (baseYaw == this.yRot && basePitch == this.xRot
			&& offset.dx() == 0.0 && offset.dy() == 0.0 && offset.dz() == 0.0
			&& offset.yaw() == 0.0F && offset.pitch() == 0.0F && offset.roll() == 0.0F) {
			return;
		}

		this.setRotation(baseYaw + offset.yaw(), basePitch + offset.pitch());

		if (offset.roll() != 0.0F) {
			Quaternionf roll = new Quaternionf().rotationZ(offset.roll() * (float) Math.PI / 180.0F);
			this.rotation.mul(roll, this.rotation);
			roll.transform(this.forwards);
			roll.transform(this.up);
			roll.transform(this.left);
		}

		this.setPosition(this.position.add(offset.dx(), offset.dy(), offset.dz()));
	}
}
