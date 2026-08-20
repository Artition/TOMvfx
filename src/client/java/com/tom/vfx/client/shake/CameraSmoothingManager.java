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
 *
 * <p>Yaw is tracked in an unwrapped (continuous) domain: the raw target and the smoothed value
 * both accumulate the shortest-path per-frame delta, so even a large lag can never flip the
 * rotation direction or make the camera "bounce" the wrong way on a fast turn.
 */
public final class CameraSmoothingManager {
	private static float lastRawYaw;
	private static float rawYawUnwrapped;
	private static float smoothedYaw;
	private static float smoothedPitch;

	private CameraSmoothingManager() {
	}

	/**
	 * Computes the smoothed camera rotation for the current frame.
	 *
	 * @param rawYaw        the camera's raw (unsmoothed) yaw in degrees
	 * @param rawPitch      the camera's raw (unsmoothed) pitch in degrees
	 * @param deltaSeconds  elapsed real time since the previous frame (seconds)
	 * @return {@code [smoothedYaw, smoothedPitch]} in degrees
	 */
	public static float[] smooth(final float rawYaw, final float rawPitch, final float deltaSeconds) {
		float yawStrength = 0.0F;
		float pitchStrength = 0.0F;
		for (VFXActiveEffect effect : VFXEffectManager.get().getActiveCinematics()) {
			float weight = effect.getWeight();
			yawStrength += effect.getParam("yaw_smoothing", 0.0F) * weight;
			pitchStrength += effect.getParam("pitch_smoothing", 0.0F) * weight;
		}

		if (yawStrength <= 0.0F && pitchStrength <= 0.0F) {
			// No smoothing active: track the raw target exactly and reset the continuous state.
			lastRawYaw = rawYaw;
			rawYawUnwrapped = rawYaw;
			smoothedYaw = rawYaw;
			smoothedPitch = rawPitch;
			return new float[]{rawYaw, rawPitch};
		}

		// Advance the raw target in continuous yaw space using the shortest per-frame step, so
		// it stays monotonic across the +/-180 boundary and never flips direction.
		float deltaYaw = Mth.wrapDegrees(rawYaw - lastRawYaw);
		lastRawYaw = rawYaw;
		rawYawUnwrapped += deltaYaw;

		if (yawStrength > 0.0F) {
			float yawFactor = 1.0F - (float) Math.exp(-yawStrength * deltaSeconds);
			smoothedYaw += (rawYawUnwrapped - smoothedYaw) * yawFactor;
		} else {
			smoothedYaw = rawYawUnwrapped;
		}

		if (pitchStrength > 0.0F) {
			float pitchFactor = 1.0F - (float) Math.exp(-pitchStrength * deltaSeconds);
			smoothedPitch += (rawPitch - smoothedPitch) * pitchFactor;
		} else {
			smoothedPitch = rawPitch;
		}

		return new float[]{Mth.wrapDegrees(smoothedYaw), Mth.wrapDegrees(smoothedPitch)};
	}
}