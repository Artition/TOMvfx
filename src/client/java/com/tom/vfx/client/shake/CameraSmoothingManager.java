package com.tom.vfx.client.shake;

import com.tom.vfx.client.effect.VFXEffectManager;
import com.tom.vfx.effect.VFXActiveEffect;
import com.tom.vfx.effect.VFXEffectType;
import net.minecraft.util.Mth;

/**
 * Cinematic camera smoothing. Applies frame-independent exponential smoothing to the camera's
 * yaw/pitch while any {@code cinematic_camera} effect is active, giving rotations a heavy,
 * inertial feel. The smoothing strength comes from the active effects' {@code yaw_smoothing} /
 * {@code pitch_smoothing} parameters (weighted by each effect's fade weight); when no effect is
 * active (or strength is 0) the target rotation is returned unchanged.
 */
public final class CameraSmoothingManager {
	private static float smoothedYaw;
	private static float smoothedPitch;

	private CameraSmoothingManager() {
	}

	/**
	 * Computes the smoothed camera rotation for the current frame.
	 *
	 * @param targetYaw     the camera's target yaw (degrees)
	 * @param targetPitch   the camera's target pitch (degrees)
	 * @param deltaSeconds  elapsed real time since the previous frame (seconds)
	 * @return {@code [smoothedYaw, smoothedPitch]} in degrees
	 */
	public static float[] smooth(final float targetYaw, final float targetPitch, final float deltaSeconds) {
		float yawStrength = 0.0F;
		float pitchStrength = 0.0F;
		for (VFXActiveEffect effect : VFXEffectManager.get().getActiveCinematics()) {
			float weight = effect.getWeight();
			yawStrength += effect.getParam("yaw_smoothing", 0.0F) * weight;
			pitchStrength += effect.getParam("pitch_smoothing", 0.0F) * weight;
		}
		if (yawStrength <= 0.0F && pitchStrength <= 0.0F) {
			// No smoothing active: track the target exactly.
			smoothedYaw = targetYaw;
			smoothedPitch = targetPitch;
			return new float[]{targetYaw, targetPitch};
		}

		// Frame-independent exponential smoothing: factor = 1 - exp(-strength * dt).
		// Use the shortest-path yaw difference so the camera never spins the long way around.
		if (yawStrength > 0.0F) {
			float yawFactor = 1.0F - (float) Math.exp(-yawStrength * deltaSeconds);
			smoothedYaw += Mth.wrapDegrees(targetYaw - smoothedYaw) * yawFactor;
		} else {
			smoothedYaw = targetYaw;
		}
		if (pitchStrength > 0.0F) {
			float pitchFactor = 1.0F - (float) Math.exp(-pitchStrength * deltaSeconds);
			smoothedPitch += (targetPitch - smoothedPitch) * pitchFactor;
		} else {
			smoothedPitch = targetPitch;
		}
		return new float[]{smoothedYaw, smoothedPitch};
	}
}