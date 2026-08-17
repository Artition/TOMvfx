package com.tom.vfx.effect;

/**
 * A single control point of an {@link AnimatedValue}: a value at a given time (in ticks)
 * together with the easing curve used to interpolate towards the next keyframe.
 *
 * @param time   time of the keyframe in ticks (relative to the animation start)
 * @param value  value at this keyframe
 * @param easing easing curve used to reach the next keyframe
 */
public record Keyframe(float time, float value, EasingType easing) {
	/**
	 * Creates a keyframe with linear interpolation.
	 *
	 * @param time  time of the keyframe in ticks
	 * @param value value at this keyframe
	 */
	public Keyframe(final float time, final float value) {
		this(time, value, EasingType.LINEAR);
	}
}
