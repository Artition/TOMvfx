package com.tom.vfx.effect;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A collection of named {@link AnimatedValue}s that together drive a single visual effect.
 * The timeline has a total duration in ticks; all values are advanced together with
 * {@link #update(float)} and queried by name with {@link #getValue(String, float)}.
 */
public class VFXTimeline {
	private final float duration;
	private final Map<String, AnimatedValue> values;
	private final Map<String, BoundParam> bindings;
	private float elapsed;

	/**
	 * Creates a timeline with the given duration and value set.
	 *
	 * @param duration total duration in ticks
	 * @param values   named animated values
	 */
	public VFXTimeline(final float duration, final Map<String, AnimatedValue> values) {
		this(duration, values, Map.of());
	}

	/**
	 * Creates a timeline with time-animated values and world bindings.
	 *
	 * @param duration total duration in ticks
	 * @param values   named animated values
	 * @param bindings named world bindings (evaluated per frame against the camera)
	 */
	public VFXTimeline(final float duration, final Map<String, AnimatedValue> values, final Map<String, BoundParam> bindings) {
		this.duration = duration;
		this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
		this.bindings = Collections.unmodifiableMap(new LinkedHashMap<>(bindings));
		this.elapsed = 0.0F;
	}

	/**
	 * Advances the whole timeline to the given elapsed time (in ticks) since the effect started.
	 *
	 * @param now elapsed time in ticks
	 */
	public void update(final float now) {
		this.elapsed = Math.max(0.0F, now);
		for (AnimatedValue value : this.values.values()) {
			value.update(this.elapsed);
		}
	}

	/**
	 * Reads the current value of the given parameter. World-bound parameters are evaluated
	 * against the camera state fed by the client.
	 *
	 * @param name     parameter name
	 * @param fallback value returned when the parameter is not present
	 */
	public float getValue(final String name, final float fallback) {
		BoundParam binding = this.bindings.get(name);
		if (binding != null) {
			return VFXWorldBindings.evaluate(binding, fallback);
		}
		AnimatedValue value = this.values.get(name);
		return value == null ? fallback : value.get();
	}

	/**
	 * True when the elapsed time reached or exceeded the timeline duration.
	 */
	public boolean isFinished() {
		return this.elapsed >= this.duration;
	}

	/**
	 * Normalized progress in {@code [0, 1]}.
	 */
	public float getProgress() {
		return this.duration <= 0.0F ? 1.0F : Math.min(1.0F, this.elapsed / this.duration);
	}

	public float getDuration() {
		return this.duration;
	}

	public float getElapsed() {
		return this.elapsed;
	}

	public Map<String, AnimatedValue> getValues() {
		return this.values;
	}

	public Map<String, BoundParam> getBindings() {
		return this.bindings;
	}
}
