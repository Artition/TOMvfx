package com.tom.vfx.effect;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

/**
 * Evaluates {@link BoundParam}s against the active camera. The client feeds the camera state
 * (position plus the view-rotation-projection matrix) every frame before post-processing runs;
 * evaluation itself is plain math and stays side-agnostic. Without a camera (e.g. on a
 * dedicated server) every binding resolves to its fallback.
 */
public final class VFXWorldBindings {
	private static final float SMOOTHING_RATE = 8.0F;
	private static volatile @Nullable Frame frame;
	private static float lastYaw;
	private static float lastPitch;
	private static float smoothedYawDelta;
	private static float smoothedPitchDelta;

	private VFXWorldBindings() {
	}

	/**
	 * Snapshot of the camera for one frame.
	 *
	 * @param camX        camera world X
	 * @param camY        camera world Y
	 * @param camZ        camera world Z
	 * @param yaw         camera yaw in degrees
	 * @param pitch       camera pitch in degrees
	 * @param viewRotProj view-rotation-projection matrix (world-relative directions to clip space)
	 */
	public record Frame(float camX, float camY, float camZ, float yaw, float pitch, Matrix4f viewRotProj) {
	}

	/**
	 * Publishes the camera state for the current frame (client only).
	 *
	 * @param deltaTicks elapsed game ticks since the previous frame
	 */
	public static void update(final float camX, final float camY, final float camZ, final float yaw, final float pitch, final Matrix4fc viewRotProj, final float deltaTicks) {
		float rawYawDelta = frame == null ? 0.0F : yaw - lastYaw;
		float rawPitchDelta = frame == null ? 0.0F : pitch - lastPitch;
		// Keep the deltas in a reasonable range to avoid huge spikes on world load/teleport.
		if (Math.abs(rawYawDelta) > 180.0F) {
			rawYawDelta = 0.0F;
		}
		if (Math.abs(rawPitchDelta) > 180.0F) {
			rawPitchDelta = 0.0F;
		}

		float alpha = deltaTicks > 0.0F ? 1.0F - (float) Math.exp(-SMOOTHING_RATE * deltaTicks) : 1.0F;
		smoothedYawDelta += (rawYawDelta - smoothedYawDelta) * alpha;
		smoothedPitchDelta += (rawPitchDelta - smoothedPitchDelta) * alpha;

		lastYaw = yaw;
		lastPitch = pitch;
		frame = new Frame(camX, camY, camZ, yaw, pitch, new Matrix4f(viewRotProj));
	}

	/**
	 * Drops the camera state (e.g. when leaving a world).
	 */
	public static void clear() {
		frame = null;
		lastYaw = 0.0F;
		lastPitch = 0.0F;
		smoothedYawDelta = 0.0F;
		smoothedPitchDelta = 0.0F;
	}

	/**
	 * Evaluates a binding against the current camera state.
	 *
	 * @param binding  the bound parameter
	 * @param fallback value returned when no camera state is available
	 * @return the evaluated value
	 */
	public static float evaluate(final BoundParam binding, final float fallback) {
		Frame current = frame;
		if (current == null) {
			return fallback;
		}
		return switch (binding.kind()) {
			case SCREEN_X, SCREEN_Y, PROXIMITY, LOOK -> evaluateSpatial(binding, current, fallback);
			case CAMERA_YAW_DELTA -> smoothedYawDelta * binding.scale();
			case CAMERA_PITCH_DELTA -> smoothedPitchDelta * binding.scale();
		};
	}

	private static float evaluateSpatial(final BoundParam binding, final Frame current, final float fallback) {
		float dx = (float) (binding.x() - current.camX());
		float dy = (float) (binding.y() - current.camY());
		float dz = (float) (binding.z() - current.camZ());
		Vector4f clip = current.viewRotProj().transform(new Vector4f(dx, dy, dz, 1.0F));
		boolean inFront = clip.w > 0.05F;
		return switch (binding.kind()) {
			case SCREEN_X -> inFront ? (clip.x / clip.w * 0.5F + 0.5F) * binding.scale() : -binding.scale();
			case SCREEN_Y -> inFront ? (clip.y / clip.w * 0.5F + 0.5F) * binding.scale() : -binding.scale();
			case PROXIMITY -> {
				if (!inFront && !binding.invert()) {
					yield 0.0F;
				}
				float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
				float t = Math.min(distance / Math.max(binding.range(), 1.0e-4F), 1.0F);
				yield (binding.invert() ? t : 1.0F - t) * binding.scale();
			}
			case LOOK -> {
				// Angle between the camera forward vector and the target direction (degrees).
				float camYawRad = (float) Math.toRadians(current.yaw());
				float camPitchRad = (float) Math.toRadians(current.pitch());
				float tgtYawRad = (float) Math.toRadians(binding.yaw());
				float tgtPitchRad = (float) Math.toRadians(binding.pitch());
				float dot = forwardY(camYawRad, camPitchRad) * forwardY(tgtYawRad, tgtPitchRad)
					+ forwardX(camYawRad, camPitchRad) * forwardX(tgtYawRad, tgtPitchRad)
					+ forwardZ(camYawRad, camPitchRad) * forwardZ(tgtYawRad, tgtPitchRad);
				float angle = (float) Math.toDegrees(Math.acos(Math.max(-1.0F, Math.min(1.0F, dot))));
				float t = Math.min(angle / Math.max(binding.range(), 1.0e-4F), 1.0F);
				yield (binding.invert() ? t : 1.0F - t) * binding.scale();
			}
			default -> fallback;
		};
	}

	private static float forwardX(final float yawRad, final float pitchRad) {
		return -(float) (Math.sin(yawRad) * Math.cos(pitchRad));
	}

	private static float forwardY(final float yawRad, final float pitchRad) {
		return -(float) Math.sin(pitchRad);
	}

	private static float forwardZ(final float yawRad, final float pitchRad) {
		return (float) (Math.cos(yawRad) * Math.cos(pitchRad));
	}
}
